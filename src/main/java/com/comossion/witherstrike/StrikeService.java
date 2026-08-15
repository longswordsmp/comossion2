package com.comossion.witherstrike;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Spawns and tracks the wither skull barrage. */
public final class StrikeService {

    private final Plugin plugin;
    private final CannonConfig config;
    private final NamespacedKey skullKey;
    private final NamespacedKey shooterKey;
    private final Random random = new Random();
    /** Skulls whose custom explosion we are currently creating, to stop the event from recursing. */
    private final Set<UUID> customExploding = new HashSet<>();

    public StrikeService(Plugin plugin, CannonConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.skullKey = new NamespacedKey(plugin, "strike_skull");
        this.shooterKey = new NamespacedKey(plugin, "strike_shooter");
    }

    /**
     * Calls a barrage down onto {@code target}.
     *
     * @return the number of skulls that will be released
     */
    public int fire(Player shooter, Location target) {
        World world = target.getWorld();
        if (world == null) {
            return 0;
        }

        Location impactCentre = target.clone();
        int total = config.skullCount();
        int waves = Math.max(1, Math.min(config.waves(), total));
        int waveDelay = Math.max(0, config.waveDelayTicks());
        // Entities above the build limit are unreliable, so keep the launch point inside it.
        double spawnY = Math.min(impactCentre.getY() + config.height(), world.getMaxHeight() - 2.0D);

        if (config.effects()) {
            world.playSound(impactCentre, Sound.ENTITY_WITHER_SPAWN, 2.0F, 0.6F);
            world.playSound(impactCentre, Sound.ENTITY_WITHER_SHOOT, 2.0F, 0.5F);
            drawTargetRing(impactCentre);
        }

        int perWave = total / waves;
        int remainder = total % waves;
        for (int wave = 0; wave < waves; wave++) {
            int amount = perWave + (wave < remainder ? 1 : 0);
            if (amount <= 0) {
                continue;
            }
            long delay = (long) wave * waveDelay;
            if (delay <= 0L) {
                spawnBatch(shooter, impactCentre, spawnY, amount);
            } else {
                plugin.getServer().getScheduler().runTaskLater(
                        plugin, () -> spawnBatch(shooter, impactCentre, spawnY, amount), delay);
            }
        }
        return total;
    }

    private void spawnBatch(Player shooter, Location target, double spawnY, int amount) {
        World world = target.getWorld();
        if (world == null) {
            return;
        }

        double spread = config.spread();
        double speed = config.speedMultiplier();
        boolean charged = config.charged();
        boolean invulnerable = config.invulnerable();
        UUID shooterId = shooter == null ? null : shooter.getUniqueId();

        for (int i = 0; i < amount; i++) {
            Vector launchOffset = randomDiscOffset(spread);
            Location spawn = new Location(world,
                    target.getX() + launchOffset.getX(),
                    spawnY,
                    target.getZ() + launchOffset.getZ());

            Vector impactOffset = config.scatterImpacts() ? randomDiscOffset(spread) : launchOffset;
            Vector direction = new Vector(
                    (target.getX() + impactOffset.getX()) - spawn.getX(),
                    target.getY() - spawn.getY(),
                    (target.getZ() + impactOffset.getZ()) - spawn.getZ());
            if (direction.lengthSquared() < 1.0E-6D) {
                direction = new Vector(0.0D, -1.0D, 0.0D);
            }

            WitherSkull skull = world.spawn(spawn, WitherSkull.class);
            skull.setCharged(charged);
            skull.setInvulnerable(invulnerable);
            if (shooter != null) {
                skull.setShooter(shooter);
            }
            // setDirection normalises and applies the vanilla wither skull acceleration (0.1/tick),
            // so leaving speed-multiplier at 1.0 gives exactly normal wither head speed.
            skull.setDirection(direction);
            if (speed != 1.0D) {
                skull.setAcceleration(skull.getAcceleration().multiply(speed));
            }

            PersistentDataContainer data = skull.getPersistentDataContainer();
            data.set(skullKey, PersistentDataType.BYTE, (byte) 1);
            if (shooterId != null) {
                data.set(shooterKey, PersistentDataType.STRING, shooterId.toString());
            }
        }
    }

    private void drawTargetRing(Location centre) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        double radius = Math.max(1.0D, config.spread() + 0.5D);
        Location ringCentre = centre.clone().add(0.0D, 0.1D, 0.0D);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 30 || !world.isChunkLoaded(ringCentre.getBlockX() >> 4, ringCentre.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }
                ticks++;
                for (int point = 0; point < 24; point++) {
                    double angle = (Math.PI * 2.0D / 24.0D) * point;
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            ringCentre.getX() + Math.cos(angle) * radius,
                            ringCentre.getY(),
                            ringCentre.getZ() + Math.sin(angle) * radius,
                            1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Uniformly distributed point inside a horizontal disc of the given radius. */
    private Vector randomDiscOffset(double radius) {
        if (radius <= 0.0D) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = radius * Math.sqrt(random.nextDouble());
        return new Vector(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    public boolean isStrikeSkull(Entity entity) {
        return entity instanceof WitherSkull
                && entity.getPersistentDataContainer().has(skullKey, PersistentDataType.BYTE);
    }

    /** The player who fired this skull, or null when the entity is not ours. */
    public UUID strikeShooter(Entity entity) {
        if (!isStrikeSkull(entity)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isExplodingCustom(Entity entity) {
        return customExploding.contains(entity.getUniqueId());
    }

    /** Replaces a skull's blast with a configured explosion, guarding against re-entry. */
    public void createCustomExplosion(Entity skull, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        UUID id = skull.getUniqueId();
        customExploding.add(id);
        try {
            world.createExplosion(location, (float) config.explosionPower(),
                    config.incendiary(), config.blockDamage(), skull);
        } finally {
            customExploding.remove(id);
        }
    }
}
