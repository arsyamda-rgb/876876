package com.veyrion.wardrobe.mirror;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Player names for messages. Identity is always the UUID; a name is only for display. */
final class Names {

    private Names() {}

    /** The last known name, or the first 8 characters of the UUID. Never asks Mojang (lookup by UUID only). */
    static String of(UUID player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        String name = offline.getName();
        return name != null ? name : player.toString().substring(0, 8);
    }

    /**
     * Finds a player by typed name: online first, then the server's cache of known names. Never blocks on the network.
     * @return the UUID, or null if the server does not know that name
     */
    static UUID find(String typed) {
        Player online = Bukkit.getPlayerExact(typed);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(typed);
        return cached == null ? null : cached.getUniqueId();
    }
}
