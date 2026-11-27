package com.veyrion.wardrobe.mirror;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One placed Aether Mirror. {@code id} is the UUID of the ItemsAdder furniture entity: it is the identity of the
 * mirror and the key of its trust list. {@code owner} is the player who placed it. {@code trusted} never holds the
 * owner. {@code isPublic} is a reserved column (always false here): trust is per mirror, never global.
 */
public record Mirror(UUID id, UUID owner, Pos pos, boolean isPublic, long createdAt, Set<UUID> trusted) {

    /** A point in a world, by world name (that is what the database stores). */
    public record Pos(String world, double x, double y, double z) {

        public double distanceSquared(Pos other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        public boolean sameWorld(Pos other) {
            return world.equals(other.world);
        }
    }

    public Mirror {
        trusted = Set.copyOf(trusted);
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isTrusted(UUID player) {
        return trusted.contains(player);
    }

    /** May this player use (open) the mirror? The owner and the players trusted on THIS mirror only. */
    public boolean canUse(UUID player) {
        return isOwner(player) || isTrusted(player);
    }

    Mirror withTrusted(UUID player) {
        Set<UUID> copy = new HashSet<>(trusted);
        copy.add(player);
        return new Mirror(id, owner, pos, isPublic, createdAt, copy);
    }

    Mirror withoutTrusted(UUID player) {
        Set<UUID> copy = new HashSet<>(trusted);
        copy.remove(player);
        return new Mirror(id, owner, pos, isPublic, createdAt, copy);
    }
}
