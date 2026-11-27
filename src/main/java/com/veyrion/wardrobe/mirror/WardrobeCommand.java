package com.veyrion.wardrobe.mirror;

import com.veyrion.wardrobe.config.Messages;
import com.veyrion.wardrobe.config.WardrobeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /wardrobe. Player commands (anyone): trust, untrust, trustlist. Admin commands ({@code veyrionwardrobe.admin}):
 * give, remove, reload, list. The target of trust/untrust/trustlist is the nearest mirror OWNED by the sender within
 * {@code mirror.target-range}; the target of remove is the nearest mirror of anyone within the same range.
 */
public final class WardrobeCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "veyrionwardrobe.admin";

    private static final int LIST_PAGE_SIZE = 8;
    private static final List<String> PLAYER_SUBS = List.of("trust", "untrust", "trustlist");
    private static final List<String> ADMIN_SUBS = List.of("give", "remove", "reload", "list");

    private final MirrorRegistry mirrors;
    private final ItemsAdderBridge itemsAdder;
    private final Supplier<WardrobeSettings> settings;
    private final Supplier<Messages> messages;
    private final Runnable reload;

    public WardrobeCommand(MirrorRegistry mirrors, ItemsAdderBridge itemsAdder, Supplier<WardrobeSettings> settings,
                           Supplier<Messages> messages, Runnable reload) {
        this.mirrors = mirrors;
        this.itemsAdder = itemsAdder;
        this.settings = settings;
        this.messages = messages;
        this.reload = reload;
    }

    private String msg(String key) {
        return messages.get().get(key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 0) {
            return usage(sender, admin);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !admin) {
            Text.send(sender, msg("no-permission"));
            return true;
        }
        switch (sub) {
            case "trust" -> trust(sender, args);
            case "untrust" -> untrust(sender, args);
            case "trustlist" -> trustList(sender);
            case "give" -> give(sender, args);
            case "remove" -> remove(sender);
            case "reload" -> {
                reload.run();
                Text.send(sender, msg("reload-done"));
            }
            case "list" -> list(sender, args);
            default -> usage(sender, admin);
        }
        return true;
    }

    private boolean usage(CommandSender sender, boolean admin) {
        String text = "/wardrobe trust <player> | untrust <player> | trustlist"
                + (admin ? " | give <player> | remove | reload | list [page]" : "");
        Text.send(sender, msg("usage"), "usage", text);
        return true;
    }

    // ------------------------------------------------------------------------------------------ player commands

    private Player playerOnly(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Text.send(sender, msg("players-only"));
        return null;
    }

    /** The nearest mirror owned by this player within the target range, or empty (and the player is told). */
    private Optional<Mirror> ownTarget(Player player) {
        double range = settings.get().mirrorTargetRange();
        Optional<Mirror> mirror = mirrors.nearest(Positions.of(player.getLocation()), range,
                candidate -> candidate.isOwner(player.getUniqueId()));
        if (mirror.isEmpty()) {
            Text.send(player, msg("no-mirror-near"), "range", formatRange(range));
        }
        return mirror;
    }

    private void trust(CommandSender sender, String[] args) {
        Player player = playerOnly(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            Text.send(player, msg("usage"), "usage", "/wardrobe trust <player>");
            return;
        }
        Optional<Mirror> mirror = ownTarget(player);
        if (mirror.isEmpty()) {
            return;
        }
        UUID target = Names.find(args[1]);
        if (target == null) {
            Text.send(player, msg("player-not-found"), "player", args[1]);
            return;
        }
        String name = Names.of(target);
        mirrors.trust(mirror.get().id(), player.getUniqueId(), target).thenAccept(status -> {
            switch (status) {
                case ADDED -> Text.send(player, msg("trust-added"), "player", name);
                case ALREADY_TRUSTED -> Text.send(player, msg("trust-already"), "player", name);
                case SELF -> Text.send(player, msg("trust-self"));
                case NOT_OWNER, NOT_FOUND -> Text.send(player, msg("no-mirror-near"), "range", formatRange(settings.get().mirrorTargetRange()));
                case ERROR -> Text.send(player, msg("trust-error"));
            }
        });
    }

    private void untrust(CommandSender sender, String[] args) {
        Player player = playerOnly(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            Text.send(player, msg("usage"), "usage", "/wardrobe untrust <player>");
            return;
        }
        Optional<Mirror> mirror = ownTarget(player);
        if (mirror.isEmpty()) {
            return;
        }
        // A trusted player who is offline and not cached still has to be removable: match the typed name against the list first.
        UUID target = null;
        for (UUID trusted : mirror.get().trusted()) {
            if (Names.of(trusted).equalsIgnoreCase(args[1])) {
                target = trusted;
                break;
            }
        }
        if (target == null) {
            target = Names.find(args[1]);
        }
        if (target == null) {
            Text.send(player, msg("player-not-found"), "player", args[1]);
            return;
        }
        String name = Names.of(target);
        mirrors.untrust(mirror.get().id(), player.getUniqueId(), target).thenAccept(status -> {
            switch (status) {
                case REMOVED -> Text.send(player, msg("untrust-removed"), "player", name);
                case NOT_TRUSTED -> Text.send(player, msg("untrust-not-trusted"), "player", name);
                case NOT_OWNER, NOT_FOUND -> Text.send(player, msg("no-mirror-near"), "range", formatRange(settings.get().mirrorTargetRange()));
                case ERROR -> Text.send(player, msg("trust-error"));
            }
        });
    }

    private void trustList(CommandSender sender) {
        Player player = playerOnly(sender);
        if (player == null) {
            return;
        }
        Optional<Mirror> mirror = ownTarget(player);
        if (mirror.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        mirror.get().trusted().forEach(id -> names.add(Names.of(id)));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            Text.send(player, msg("trustlist-empty"));
            return;
        }
        Text.send(player, msg("trustlist-header"), "count", String.valueOf(names.size()));
        names.forEach(name -> Text.send(player, msg("trustlist-entry"), "player", name));
    }

    // ------------------------------------------------------------------------------------------ admin commands

    private void give(CommandSender sender, String[] args) {
        if (args.length != 2) {
            Text.send(sender, msg("usage"), "usage", "/wardrobe give <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, msg("player-not-found"), "player", args[1]);
            return;
        }
        String id = settings.get().mirrorItemId();
        Optional<ItemStack> item = itemsAdder.newItem(id);
        if (item.isEmpty()) {
            Text.send(sender, msg("give-item-missing"), "id", id);
            return;
        }
        Map<Integer, ItemStack> left = target.getInventory().addItem(item.get());
        left.values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        Text.send(sender, msg("give-done"), "player", target.getName());
        if (!target.equals(sender)) {
            Text.send(target, msg("give-received"));
        }
    }

    private void remove(CommandSender sender) {
        Player admin = playerOnly(sender);
        if (admin == null) {
            return;
        }
        double range = settings.get().mirrorTargetRange();
        Optional<Mirror> mirror = mirrors.nearest(Positions.of(admin.getLocation()), range, candidate -> true);
        if (mirror.isEmpty()) {
            Text.send(admin, msg("remove-none"), "range", formatRange(range));
            return;
        }
        Mirror target = mirror.get();
        String owner = Names.of(target.owner());
        mirrors.unregister(target.id()); // first: the record is gone even if removing the furniture raises an event
        Entity entity = Bukkit.getEntity(target.id());
        boolean removed = entity != null && itemsAdder.removeFurniture(entity, false);
        Text.send(admin, msg(removed ? "remove-done" : "remove-record-only"), "player", owner);
    }

    private void list(CommandSender sender, String[] args) {
        List<Mirror> all = mirrors.all();
        if (all.isEmpty()) {
            Text.send(sender, msg("list-empty"));
            return;
        }
        int pages = (all.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                page = 1;
            }
        }
        Text.send(sender, msg("list-header"), "count", String.valueOf(all.size()), "page", String.valueOf(page),
                "pages", String.valueOf(pages));
        int from = (page - 1) * LIST_PAGE_SIZE;
        for (int i = from; i < Math.min(all.size(), from + LIST_PAGE_SIZE); i++) {
            Mirror m = all.get(i);
            Text.send(sender, msg("list-entry"), "index", String.valueOf(i + 1), "player", Names.of(m.owner()),
                    "world", m.pos().world(), "x", String.valueOf(Math.round(m.pos().x())),
                    "y", String.valueOf(Math.round(m.pos().y())), "z", String.valueOf(Math.round(m.pos().z())),
                    "trusted", String.valueOf(m.trusted().size()),
                    "state", Bukkit.getEntity(m.id()) != null ? "loaded" : "not loaded",
                    "id", m.id().toString());
        }
    }

    // ------------------------------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            addMatching(out, PLAYER_SUBS, args[0]);
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                addMatching(out, ADMIN_SUBS, args[0]);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            boolean wantsPlayer = sub.equals("trust") || sub.equals("untrust")
                    || (sub.equals("give") && sender.hasPermission(ADMIN_PERMISSION));
            if (wantsPlayer) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                addMatching(out, names, args[1]);
            }
        }
        return out;
    }

    private static void addMatching(List<String> out, List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
    }

    private static String formatRange(double range) {
        return range == Math.floor(range) ? String.valueOf((int) range) : String.format(Locale.ROOT, "%.1f", range);
    }
}
