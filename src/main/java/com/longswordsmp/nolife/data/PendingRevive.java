package com.longswordsmp.nolife.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A revive location stored for a player who is currently offline. When they
 * next log in they are teleported here and the totem effects play.
 */
public record PendingRevive(String world, double x, double y, double z, float yaw, float pitch) {

    public static PendingRevive of(Location location) {
        return new PendingRevive(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /** Resolve to a live {@link Location}, or {@code null} if the world is gone. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z, yaw, pitch);
    }
}
