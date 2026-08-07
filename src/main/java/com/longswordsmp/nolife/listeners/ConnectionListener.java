package com.longswordsmp.nolife.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Handles login (blocking eliminated players), join (data + display + pending
 * revive + resource pack), respawn (safety net) and quit (save).
 */
public class ConnectionListener implements Listener {

    private final NoLifePlugin plugin;

    public ConnectionListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        if (plugin.lives().isEliminated(event.getPlayer().getUniqueId())) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, plugin.config().msg("ban-kick"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.lives().ensureData(player);
        plugin.lives().updateDisplay(player);
        plugin.lives().applyPendingRevive(player);
        plugin.resourcePacks().sendOnJoin(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Safety net: if a player somehow respawns while still eliminated (e.g.
        // they clicked respawn before the kick fired), lock them into spectator.
        if (plugin.lives().isEliminated(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.lives().isEliminated(player.getUniqueId())) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.data().save();
    }
}
