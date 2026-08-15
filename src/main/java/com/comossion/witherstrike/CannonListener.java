package com.comossion.witherstrike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/** Wires the cannon rod up to the strike service. */
public final class CannonListener implements Listener {

    private static final String USE_PERMISSION = "witherstrikecannon.use";

    private final Plugin plugin;
    private final CannonConfig config;
    private final CannonItem item;
    private final StrikeService strikes;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CannonListener(Plugin plugin, CannonConfig config, CannonItem item, StrikeService strikes) {
        this.plugin = plugin;
        this.config = config;
        this.item = item;
        this.strikes = strikes;
    }

    // ---------------------------------------------------------------- bobber mode

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (item.heldIn(player) == null) {
            return;
        }

        if (config.targetingMode() != CannonConfig.TargetingMode.BOBBER) {
            event.setCancelled(true);
            return;
        }

        switch (event.getState()) {
            case FISHING -> {
                if (!canFire(player)) {
                    event.setCancelled(true);
                    return;
                }
                trackHook(player, event.getHook());
            }
            // A weapon this size does not also catch salmon.
            case CAUGHT_FISH, CAUGHT_ENTITY, BITE, LURED -> event.setCancelled(true);
            default -> {
            }
        }
    }

    /** Watches a cast bobber and fires as soon as it settles on ground, water or an entity. */
    private void trackHook(Player player, FishHook hook) {
        int timeout = config.hookTimeoutTicks();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !hook.isValid()) {
                    // Reeled in before it landed - no strike.
                    cancel();
                    return;
                }

                ticks++;
                Entity hooked = hook.getHookedEntity();
                Location location = hook.getLocation();
                boolean settled = hooked != null
                        || hook.isOnGround()
                        || location.getBlock().isLiquid()
                        || (ticks > 3 && hook.getVelocity().lengthSquared() < 0.0016D);

                if (!settled && ticks < timeout) {
                    return;
                }

                cancel();
                Location target = hooked != null ? hooked.getLocation() : location;
                hook.remove();
                launch(player, target);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---------------------------------------------------------------- raytrace mode

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (config.targetingMode() != CannonConfig.TargetingMode.RAYTRACE) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getHand() == null || event.getHand() != item.heldIn(player)) {
            return;
        }

        // Stop the rod from casting a bobber as well.
        event.setCancelled(true);
        if (!canFire(player)) {
            return;
        }

        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), config.maxDistance(), FluidCollisionMode.NEVER, true);
        if (result == null) {
            player.sendMessage(config.message("no-target"));
            return;
        }
        launch(player, result.getHitPosition().toLocation(player.getWorld()));
    }

    // ---------------------------------------------------------------- firing

    private boolean canFire(Player player) {
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(config.message("no-permission"));
            return false;
        }
        if (config.isWorldDisabled(player.getWorld().getName())) {
            player.sendMessage(config.message("world-disabled"));
            return false;
        }
        long remaining = cooldownRemainingMillis(player);
        if (remaining > 0L) {
            long seconds = (remaining + 999L) / 1000L;
            player.sendMessage(config.message("cooldown", "%seconds%", String.valueOf(seconds)));
            return false;
        }
        return true;
    }

    private void launch(Player player, Location target) {
        int fired = strikes.fire(player, target);
        if (fired <= 0) {
            return;
        }
        applyCooldown(player);
        player.sendMessage(config.message("target-locked", "%count%", String.valueOf(fired)));
    }

    private long cooldownRemainingMillis(Player player) {
        Long readyAt = cooldowns.get(player.getUniqueId());
        if (readyAt == null) {
            return 0L;
        }
        return Math.max(0L, readyAt - System.currentTimeMillis());
    }

    private void applyCooldown(Player player) {
        int seconds = config.cooldownSeconds();
        if (seconds <= 0) {
            return;
        }
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.setCooldown(Material.FISHING_ROD, seconds * 20);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- impact handling

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !strikes.isStrikeSkull(entity) || strikes.isExplodingCustom(entity)) {
            return;
        }

        if (config.explosionPower() > 0.0D) {
            event.setCancelled(true);
            strikes.createCustomExplosion(entity, event.getLocation());
            return;
        }
        if (!config.blockDamage()) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.protectShooter() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID shooter = strikes.strikeShooter(event.getDamager());
        if (shooter != null && shooter.equals(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
