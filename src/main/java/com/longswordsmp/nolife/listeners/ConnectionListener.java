package com.longswordsmp.nolife.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.data.PendingRevive;

/**
 * Handles login (blocking eliminated players), join (data + display + pending
 * revive + resource pack), respawn (pending revive / safety net) and quit (save).
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
        UUID id = player.getUniqueId();

        if (plugin.lives().isEliminated(id)) {
            // Safety net: if a player somehow respawns while still eliminated
            // (e.g. they clicked respawn before the kick fired), lock them into
            // spectator until the kick removes them.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.lives().isEliminated(id)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
            return;
        }

        // A revive that happened while the player was still dead on the death
        // screen leaves a pending location. Respawn them right at the reviver
        // and play the totem effects.
        PendingRevive pending = plugin.data().getPending(id);
        if (pending != null) {
            Location loc = pending.toLocation();
            if (loc != null) {
                event.setRespawnLocation(loc);
            }
            plugin.data().removePending(id);
            plugin.data().save();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SURVIVAL);
                    plugin.lives().updateDisplay(player);
                    plugin.lives().playReviveEffects(player);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.data().save();
    }
}
