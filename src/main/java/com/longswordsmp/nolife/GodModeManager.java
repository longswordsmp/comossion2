package com.longswordsmp.nolife;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players currently have god mode (no damage) enabled.
 *
 * <p>State is in-memory only, so god mode clears on a server restart - a
 * deliberate choice so an admin can never be left silently invulnerable across
 * a restart with no visible indication.</p>
 */
public class GodModeManager {

    private final Set<UUID> god = new HashSet<>();

    public boolean isGod(UUID id) {
        return god.contains(id);
    }

    public void set(UUID id, boolean enabled) {
        if (enabled) {
            god.add(id);
        } else {
            god.remove(id);
        }
    }

    /** Flip a player's god mode and return the new state. */
    public boolean toggle(UUID id) {
        if (god.contains(id)) {
            god.remove(id);
            return false;
        }
        god.add(id);
        return true;
    }
}
