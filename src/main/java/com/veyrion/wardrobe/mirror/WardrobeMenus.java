package com.veyrion.wardrobe.mirror;

import com.veyrion.wardrobe.config.Messages;
import com.veyrion.wardrobe.config.WardrobeSettings;
import com.veyrion.wardrobe.model.Outfit;
import com.veyrion.wardrobe.outfit.OutfitManager;
import com.veyrion.wardrobe.skin.SkinManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * The wardrobe window opened by the mirror: a plain chest menu listing the outfits of the player who USES the mirror
 * (never the mirror owner's), where a click wears one. The ItemsAdder textured GUI, the 3D preview and "add outfit"
 * come in Stage 4; this menu is what the mirror opens until then.
 * <p>
 * While a menu is open it is re-checked every 10 ticks: if the player walked further than {@code mirror.max-use-distance},
 * the mirror is gone or their permission was taken back, the menu closes. Main thread only.
 */
public final class WardrobeMenus implements Listener {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    /** Identifies our inventories. {@code inventory} is set once, right after it is created. */
    private static final class Holder implements InventoryHolder {
        final UUID mirrorId;
        final UUID user;
        int page;
        Inventory inventory;
        /** outfit id per slot (0..44), -1 = no outfit there. */
        final long[] outfitAt = new long[PER_PAGE];

        Holder(UUID mirrorId, UUID user) {
            this.mirrorId = mirrorId;
            this.user = user;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final MirrorRegistry mirrors;
    private final OutfitManager outfits;
    private final Supplier<SkinManager> skins;
    private final Supplier<WardrobeSettings> settings;
    private final Supplier<Messages> messages;
    private final Map<UUID, Holder> open = new HashMap<>();

    public WardrobeMenus(Plugin plugin, MirrorRegistry mirrors, OutfitManager outfits, Supplier<SkinManager> skins,
                         Supplier<WardrobeSettings> settings, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.mirrors = mirrors;
        this.outfits = outfits;
        this.skins = skins;
        this.settings = settings;
        this.messages = messages;
        Bukkit.getScheduler().runTaskTimer(plugin, this::validateOpenMenus, 10L, 10L);
    }

    /** Opens the menu. The caller has already checked that this player may use this mirror. */
    public void open(Player user, Mirror mirror) {
        if (!outfits.isLoaded(user.getUniqueId())) {
            Text.send(user, messages.get().get("wardrobe-loading"));
            return;
        }
        Holder holder = new Holder(mirror.id(), user.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE, Text.render(messages.get().get("menu-title")));
        open.put(user.getUniqueId(), holder);
        render(holder);
        user.openInventory(holder.inventory);
    }

    public void closeAll() {
        for (Holder holder : new ArrayList<>(open.values())) {
            Player player = Bukkit.getPlayer(holder.user);
            if (player != null) {
                player.closeInventory();
            }
        }
        open.clear();
    }

    public void onQuit(UUID player) {
        open.remove(player);
    }

    // ------------------------------------------------------------------------------------------ drawing

    private void render(Holder holder) {
        Inventory inv = holder.inventory;
        inv.clear();
        java.util.Arrays.fill(holder.outfitAt, -1L);
        List<Outfit> list = outfits.outfits(holder.user);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));
        Long activeId = outfits.active(holder.user).map(Outfit::id).orElse(null);

        int from = holder.page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && from + slot < list.size(); slot++) {
            Outfit outfit = list.get(from + slot);
            holder.outfitAt[slot] = outfit.id();
            boolean active = activeId != null && activeId == outfit.id();
            inv.setItem(slot, icon(Material.LEATHER_CHESTPLATE, Component.text(outfit.name(),
                            active ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                    List.of(Component.text(outfit.model().name().equals("SLIM") ? "Slim" : "Wide", NamedTextColor.GRAY),
                            Component.text(active ? "Sedang dipakai" : "Klik untuk memakai",
                                    active ? NamedTextColor.GREEN : NamedTextColor.YELLOW)), active));
        }
        if (list.isEmpty()) {
            inv.setItem(22, icon(Material.BARRIER, Text.render(messages.get().get("menu-empty")), List.of(), false));
        }
        if (holder.page > 0) {
            inv.setItem(SLOT_PREV, icon(Material.ARROW, Component.text("<", NamedTextColor.WHITE), List.of(), false));
        }
        inv.setItem(SLOT_CLOSE, icon(Material.RED_STAINED_GLASS_PANE, Component.text("Tutup", NamedTextColor.RED), List.of(), false));
        if (holder.page < pages - 1) {
            inv.setItem(SLOT_NEXT, icon(Material.ARROW, Component.text(">", NamedTextColor.WHITE), List.of(), false));
        }
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (Component line : lore) {
            lines.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true); // nothing is ever moved in or out of this menu
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.user)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!stillAllowed(player, holder)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == SLOT_PREV && holder.page > 0) {
            holder.page--;
            render(holder);
        } else if (slot == SLOT_NEXT) {
            holder.page++;
            render(holder);
        } else if (slot >= 0 && slot < PER_PAGE && holder.outfitAt[slot] >= 0) {
            wear(player, holder, holder.outfitAt[slot]);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            open.remove(holder.user, holder);
        }
    }

    private void wear(Player player, Holder holder, long outfitId) {
        SkinManager manager = skins.get();
        if (manager == null) {
            return;
        }
        // The id comes from THIS player's own list, and SkinManager/OutfitManager look it up by this player's UUID only.
        manager.wear(player.getUniqueId(), outfitId).thenAccept(result -> {
            if (player.isOnline()) {
                player.sendMessage(Component.text(result.message(), result.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            if (open.get(holder.user) == holder) {
                render(holder);
            }
        });
    }

    // ------------------------------------------------------------------------------------------ re-checking

    /** Closes the menu (with the reason) if this player may no longer use it. @return true if all is fine */
    private boolean stillAllowed(Player player, Holder holder) {
        Optional<Mirror> mirror = mirrors.find(holder.mirrorId);
        if (mirror.isEmpty()) {
            return deny(player, "mirror-gone");
        }
        if (!mirror.get().canUse(player.getUniqueId())) {
            return deny(player, "mirror-access-lost");
        }
        Mirror.Pos from = Positions.of(player.getLocation());
        double max = settings.get().mirrorMaxUseDistance();
        if (!mirror.get().pos().sameWorld(from) || mirror.get().pos().distanceSquared(from) > max * max) {
            return deny(player, "mirror-too-far");
        }
        return true;
    }

    private boolean deny(Player player, String messageKey) {
        player.closeInventory();
        Text.send(player, messages.get().get(messageKey));
        return false;
    }

    private void validateOpenMenus() {
        for (Holder holder : new ArrayList<>(open.values())) {
            Player player = Bukkit.getPlayer(holder.user);
            if (player == null || !player.isOnline()) {
                open.remove(holder.user, holder);
            } else {
                stillAllowed(player, holder);
            }
        }
    }
}
