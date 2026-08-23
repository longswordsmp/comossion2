package com.longswordsmp.nolife.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Handles two damage rules:
 * <ul>
 *   <li>God mode: players with {@code /godmode} on never take damage.</li>
 *   <li>Natural-death protection: when disabled by an admin, only another
 *       player can deal a killing blow - environmental damage (fall, lava,
 *       drowning, mobs, ...) is capped so it can never kill.</li>
 * </ul>
 */
public class DamageListener implements Listener {

    private final NoLifePlugin plugin;

    public DamageListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // God mode: cancel all incoming damage outright.
        if (plugin.godMode().isGod(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Natural-death protection: only kicks in when natural deaths are OFF.
        if (plugin.data().isNaturalDeathsAllowed()) {
            return;
        }
        // Only intervene on a blow that would actually be fatal.
        if (player.getHealth() - event.getFinalDamage() > 0.0) {
            return;
        }
        // A kill by another player still counts - that's not a "natural" death.
        if (isPlayerCaused(event)) {
            return;
        }

        // Stop the death: cancel the lethal hit and put out any fire so a
        // follow-up fire tick can't immediately re-kill them.
        event.setCancelled(true);
        player.setFireTicks(0);

        // The void is the one cause that would just re-damage them next tick,
        // so move them somewhere solid instead of leaving them falling.
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location spawn = player.getWorld().getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
    }

    /** True when the damage was dealt by another player (melee or projectile). */
    private boolean isPlayerCaused(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player) {
                return true;
            }
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
                return true;
            }
        }
        return false;
    }
}
