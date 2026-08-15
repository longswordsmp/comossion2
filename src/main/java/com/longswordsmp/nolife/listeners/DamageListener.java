package com.longswordsmp.nolife.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Cancels all damage to players who currently have god mode enabled
 * ({@code /godmode}).
 */
public class DamageListener implements Listener {

    private final NoLifePlugin plugin;

    public DamageListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.godMode().isGod(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
