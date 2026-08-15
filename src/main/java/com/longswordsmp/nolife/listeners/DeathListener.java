package com.longswordsmp.nolife.listeners;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Decrements a life on every death, eliminates the player when they run out,
 * and pays out any bounty on the victim to their killer.
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

        // A kill claims the whole bounty, regardless of whether this death also
        // eliminates the victim below.
        payBounty(player);

        if (plugin.lives().isEliminated(uuid)) {
            return; // already out; nothing to subtract
        }
        if (plugin.lives().isInfinite(uuid)) {
            return; // admin/infinite lives: never lose a life
        }

        int next = plugin.lives().getLivesOrDefault(uuid) - 1;
        if (next >= 1) {
            plugin.lives().setLives(uuid, next, player.getName());
            player.sendMessage(plugin.config().msg("life-lost", "%lives%", String.valueOf(next)));
        } else {
            plugin.lives().eliminate(uuid, player.getName(), true);
        }
    }

    /** If a player killed the victim, hand that killer every staked Life Gem. */
    private void payBounty(Player victim) {
        if (!plugin.config().bountyEnabled()) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return; // environmental death, or the killer is somehow the victim
        }
        if (plugin.bounties().get(victim.getUniqueId()) == null) {
            return;
        }
        int reward = plugin.bounties().claimAll(victim.getUniqueId());
        if (reward <= 0) {
            return;
        }
        plugin.items().giveLifeGems(killer, reward);
        killer.sendMessage(plugin.config().msg("bounty-claimed-self",
                "%amount%", String.valueOf(reward), "%player%", victim.getName()));
        if (plugin.config().bountyBroadcastClaimed()) {
            broadcast(plugin.config().msg("bounty-claimed",
                    "%killer%", killer.getName(),
                    "%player%", victim.getName(),
                    "%amount%", String.valueOf(reward)));
        }
    }

    private void broadcast(Component component) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }
}
