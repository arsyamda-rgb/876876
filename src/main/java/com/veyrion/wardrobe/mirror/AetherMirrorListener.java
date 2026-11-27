package com.veyrion.wardrobe.mirror;

import com.veyrion.wardrobe.config.Messages;
import com.veyrion.wardrobe.config.WardrobeSettings;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.FurniturePlaceSuccessEvent;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Connects the physical furniture to the wardrobe data. Only furniture whose ItemsAdder id equals
 * {@code mirror.item-id} is touched; every other furniture is ignored.
 * <ul>
 *   <li>placed: the player becomes the owner, saved under the furniture entity's UUID</li>
 *   <li>right click: owner or trusted player opens the wardrobe; anyone else is told no</li>
 *   <li>broken: only the owner (or an admin) may; afterwards the record and its trust list are deleted</li>
 * </ul>
 */
public final class AetherMirrorListener implements Listener {

    private final Logger logger;
    private final MirrorRegistry mirrors;
    private final ItemsAdderBridge itemsAdder;
    private final WardrobeMenus menus;
    private final Supplier<WardrobeSettings> settings;
    private final Supplier<Messages> messages;
    private final String adminPermission;

    public AetherMirrorListener(Logger logger, MirrorRegistry mirrors, ItemsAdderBridge itemsAdder, WardrobeMenus menus,
                                Supplier<WardrobeSettings> settings, Supplier<Messages> messages, String adminPermission) {
        this.logger = logger;
        this.mirrors = mirrors;
        this.itemsAdder = itemsAdder;
        this.menus = menus;
        this.settings = settings;
        this.messages = messages;
        this.adminPermission = adminPermission;
    }

    private boolean isMirror(String namespacedId) {
        return namespacedId != null && namespacedId.equals(settings.get().mirrorItemId());
    }

    // ------------------------------------------------------------------------------------------ placing

    /**
     * ItemsAdder 4.0.3: FurniturePlaceSuccessEvent ("called after simple furniture placement succeeds") exists since
     * ItemsAdder 3.2.3c. It is not Cancellable, so it only fires for a placement that really happened. Later versions
     * deprecate it in favour of FurniturePlacedEvent; that class is not used here because it is not verified for 4.0.3.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaced(FurniturePlaceSuccessEvent event) {
        if (!isMirror(event.getNamespacedID()) || event.getBukkitEntity() == null) {
            return;
        }
        Player player = event.getPlayer();
        Entity entity = event.getBukkitEntity();
        UUID id = entity.getUniqueId();
        Mirror.Pos pos = Positions.of(entity.getLocation());
        mirrors.register(id, player.getUniqueId(), pos, System.currentTimeMillis()).thenAccept(saved -> {
            if (saved) {
                Text.send(player, messages.get().get("mirror-placed"));
                return;
            }
            if (mirrors.find(id).isPresent()) {
                return; // registered earlier (the same furniture): nothing to undo
            }
            // The mirror could not be saved: an unregistered mirror would be useless, so take it back and give the item back.
            Entity current = Bukkit.getEntity(id);
            boolean removed = current != null && itemsAdder.removeFurniture(current, false);
            if (removed) {
                giveBack(player);
                Text.send(player, messages.get().get("mirror-place-failed"));
            } else {
                logger.severe("Mirror " + id + " could not be saved and could not be removed again; remove it with /iaremove.");
            }
        });
    }

    private void giveBack(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // creative players do not lose the item when placing
        }
        Optional<ItemStack> item = itemsAdder.newItem(settings.get().mirrorItemId());
        if (item.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(item.get());
        left.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    // ------------------------------------------------------------------------------------------ using

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        if (!isMirror(event.getNamespacedID()) || event.getBukkitEntity() == null) {
            return;
        }
        // FurnitureInteractEvent#getAction() was added in ItemsAdder 4.0.17 and does not exist in 4.0.3, so every
        // interact event on the mirror counts as "use". Breaking goes through FurnitureBreakEvent below.
        event.setCancelled(true); // the mirror does nothing else (no sitting, no default interaction)
        Player player = event.getPlayer();
        Optional<Mirror> mirror = mirrors.find(event.getBukkitEntity().getUniqueId());
        if (mirror.isEmpty()) {
            Text.send(player, messages.get().get("mirror-not-registered"));
            return;
        }
        if (!mirror.get().canUse(player.getUniqueId())) {
            Text.send(player, messages.get().get("mirror-no-access"), "player", Names.of(mirror.get().owner()));
            return;
        }
        menus.open(player, mirror.get());
    }

    // ------------------------------------------------------------------------------------------ breaking

    /** HIGH: decides who may break a registered mirror. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakCheck(FurnitureBreakEvent event) {
        if (!isMirror(event.getNamespacedID()) || event.getBukkitEntity() == null) {
            return;
        }
        Player player = event.getPlayer();
        Optional<Mirror> mirror = mirrors.find(event.getBukkitEntity().getUniqueId());
        if (player == null || mirror.isEmpty()) {
            return; // not ours to judge (no player, or an unregistered mirror)
        }
        if (!mirror.get().isOwner(player.getUniqueId()) && !player.hasPermission(adminPermission)) {
            event.setCancelled(true);
            Text.send(player, messages.get().get("mirror-break-denied"), "player", Names.of(mirror.get().owner()));
        }
    }

    /**
     * MONITOR + ignoreCancelled: runs only if the break really goes through (no other plugin cancelled it), so a
     * mirror that stays standing never loses its record.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(FurnitureBreakEvent event) {
        if (!isMirror(event.getNamespacedID()) || event.getBukkitEntity() == null) {
            return;
        }
        mirrors.unregister(event.getBukkitEntity().getUniqueId());
    }

    // ------------------------------------------------------------------------------------------ ItemsAdder loading

    /** ItemsAdder (re)loaded its data: now the furniture id can be checked, and a wrong id is reported once and clearly. */
    @EventHandler
    public void onItemsAdderLoaded(ItemsAdderLoadDataEvent event) {
        String id = settings.get().mirrorItemId();
        if (itemsAdder.isRegistered(id)) {
            logger.info("ItemsAdder loaded: furniture " + id + " found, " + mirrors.size() + " mirror(s) registered.");
        } else {
            logger.warning("ItemsAdder loaded, but the furniture \"" + id + "\" does not exist. Mirrors cannot be given or placed. "
                    + "Copy contents/aether_mirror into plugins/ItemsAdder/contents/, run /iazip, or fix mirror.item-id in config.yml.");
        }
    }
}
