package com.veyrion.wardrobe.mirror;

import org.bukkit.Location;

/** Bukkit Location to the plain {@link Mirror.Pos}. */
final class Positions {

    private Positions() {}

    static Mirror.Pos of(Location location) {
        return new Mirror.Pos(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }
}
