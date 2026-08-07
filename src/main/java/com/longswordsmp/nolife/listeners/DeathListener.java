package com.longswordsmp.nolife.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Decrements a life on every death and eliminates the player when they run out.
 */
public class DeathListener implements Listener {

    private final NoLifePlugin plugin;

    public DeathListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (plugin.lives().isEliminated(uuid)) {
            return; // already out; nothing to subtract
        }

        int next = plugin.lives().getLivesOrDefault(uuid) - 1;
        if (next >= 1) {
            plugin.lives().setLives(uuid, next, player.getName());
            player.sendMessage(plugin.config().msg("life-lost", "%lives%", String.valueOf(next)));
        } else {
            plugin.lives().eliminate(uuid, player.getName(), true);
        }
    }
}
