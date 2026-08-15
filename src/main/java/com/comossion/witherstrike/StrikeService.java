package com.comossion.witherstrike;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Spawns the wither skull barrage and turns the impacts into a crater.
 *
 * <p>In {@link CannonConfig.ExplosionMode#CUSTOM} mode the vanilla blast is cancelled and we
 * carve the terrain ourselves. That is both far more destructive and much cheaper than 1500
 * vanilla explosions, and it stops the explosions from knocking the still-falling skulls back
 * into the sky.
 */
public final class StrikeService {

    /** Blocks that survive an orbital strike. Resolved by name so a missing one is just skipped. */
    private static final String[] INDESTRUCTIBLE_NAMES = {
            "BEDROCK", "BARRIER", "END_PORTAL", "END_PORTAL_FRAME", "END_GATEWAY", "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
            "JIGSAW", "LIGHT", "MOVING_PISTON", "REINFORCED_DEEPSLATE"
    };

    private final Plugin plugin;
    private final CannonConfig config;
    private final NamespacedKey skullKey;
    private final NamespacedKey shooterKey;
    private final Random random = new Random();
    private final Set<Material> indestructible = new HashSet<>();

    /** Skulls whose custom explosion we are currently creating, to stop the event from recursing. */
    private final Set<UUID> customExploding = new HashSet<>();

    /** Blocks waiting to be removed, per world. LinkedHashSet dedupes overlapping blasts for free. */
    private final Map<World, LinkedHashSet<Long>> carveQueues = new HashMap<>();
    /** Impact points already carved, quantised to a grid, so 1500 skulls don't carve 1500 spheres. */
    private final Set<Long> carvedCentres = new HashSet<>();
    private int queuedBlocks = 0;
    private BukkitTask carveTask;
    private int impactCounter = 0;

    public StrikeService(Plugin plugin, CannonConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.skullKey = new NamespacedKey(plugin, "strike_skull");
        this.shooterKey = new NamespacedKey(plugin, "strike_shooter");
        for (String name : INDESTRUCTIBLE_NAMES) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                indestructible.add(material);
            }
        }
    }

    // ------------------------------------------------------------------ firing

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
            world.playSound(impactCentre, Sound.ENTITY_WITHER_SPAWN, 4.0F, 0.5F);
            world.playSound(impactCentre, Sound.ENTITY_WITHER_SHOOT, 4.0F, 0.4F);
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

        scheduleDamagePulses(shooter, impactCentre);
        return total;
    }

    private void spawnBatch(Player shooter, Location target, double spawnY, int amount) {
        World world = target.getWorld();
        if (world == null) {
            return;
        }

        double spread = config.spread();
        double jitter = config.impactJitter();
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

            // Aim at the launch column plus a little jitter, so the skulls fall near vertically
            // instead of streaking across a 40 block wide circle at an angle.
            Vector impactOffset = randomDiscOffset(jitter);
            Vector direction = new Vector(
                    (target.getX() + launchOffset.getX() + impactOffset.getX()) - spawn.getX(),
                    target.getY() - spawn.getY(),
                    (target.getZ() + launchOffset.getZ() + impactOffset.getZ()) - spawn.getZ());
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

    // ------------------------------------------------------------------ impacts

    /** A strike skull just detonated; queue the crater and play the boom. */
    public void onImpact(Entity skull, Location impact) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }

        impactCounter++;
        if (config.effects() && impactCounter % config.impactEffectEvery() == 0) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER,
                    impact.getX(), impact.getY(), impact.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (impactCounter % (config.impactEffectEvery() * 8) == 0) {
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 4.0F, 0.6F);
            }
        }

        queueCrater(world, impact, config.blastRadius());
    }

    /**
     * Queues a sphere of blocks for removal. Impacts are quantised onto a grid first, so a
     * barrage of 1500 skulls only carves a few hundred spheres instead of 1500 overlapping ones.
     */
    private void queueCrater(World world, Location impact, double radius) {
        int centreX = impact.getBlockX();
        int centreY = impact.getBlockY();
        int centreZ = impact.getBlockZ();

        int grid = Math.max(1, (int) (radius * 0.75D));
        if (!carvedCentres.add(pack(Math.floorDiv(centreX, grid), Math.floorDiv(centreY, grid), Math.floorDiv(centreZ, grid)))) {
            return;
        }

        int max = config.maxBlocksPerStrike();
        if (queuedBlocks >= max) {
            return;
        }

        LinkedHashSet<Long> queue = carveQueues.computeIfAbsent(world, ignored -> new LinkedHashSet<>());
        int reach = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                int y = centreY + dy;
                if (y < minY || y > maxY) {
                    continue;
                }
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                        continue;
                    }
                    if (queuedBlocks >= max) {
                        ensureCarveTask();
                        return;
                    }
                    if (queue.add(pack(centreX + dx, y, centreZ + dz))) {
                        queuedBlocks++;
                    }
                }
            }
        }
        ensureCarveTask();
    }

    private void ensureCarveTask() {
        if (carveTask != null) {
            return;
        }
        carveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::carveTick, 1L, 1L);
    }

    /** Removes up to blocks-per-tick queued blocks, so a 150k block crater doesn't freeze the server. */
    private void carveTick() {
        int budget = config.blocksPerTick();
        boolean carveLiquids = config.carveLiquids();

        Iterator<Map.Entry<World, LinkedHashSet<Long>>> worlds = carveQueues.entrySet().iterator();
        while (budget > 0 && worlds.hasNext()) {
            Map.Entry<World, LinkedHashSet<Long>> entry = worlds.next();
            World world = entry.getKey();
            Iterator<Long> blocks = entry.getValue().iterator();

            while (budget > 0 && blocks.hasNext()) {
                long key = blocks.next();
                blocks.remove();
                queuedBlocks--;
                budget--;

                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type == Material.AIR || indestructible.contains(type)) {
                    continue;
                }
                if (!carveLiquids && block.isLiquid()) {
                    continue;
                }
                block.setType(Material.AIR, false);
            }

            if (entry.getValue().isEmpty()) {
                worlds.remove();
            }
        }

        if (carveQueues.isEmpty()) {
            queuedBlocks = 0;
            carvedCentres.clear();
            impactCounter = 0;
            if (carveTask != null) {
                carveTask.cancel();
                carveTask = null;
            }
        }
    }

    // ------------------------------------------------------------------ area damage

    /** Repeatedly hurts everything standing in the strike zone while the barrage lands. */
    private void scheduleDamagePulses(Player shooter, Location centre) {
        int pulses = config.damagePulses();
        double damage = config.damagePerPulse();
        if (pulses <= 0 || damage <= 0.0D) {
            return;
        }
        double radius = config.spread() + config.blastRadius();
        UUID shooterId = shooter == null ? null : shooter.getUniqueId();
        boolean protectShooter = config.protectShooter();
        long firstImpact = (long) (config.height() / 2.0D) + 10L;

        new BukkitRunnable() {
            int fired = 0;

            @Override
            public void run() {
                if (fired >= pulses) {
                    cancel();
                    return;
                }
                fired++;
                World world = centre.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }
                for (Entity entity : world.getNearbyEntities(centre, radius, 128.0D, radius)) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    if (protectShooter && shooterId != null && shooterId.equals(entity.getUniqueId())) {
                        continue;
                    }
                    living.damage(damage, shooter);
                }
            }
        }.runTaskTimer(plugin, firstImpact, config.damagePulseIntervalTicks());
    }

    // ------------------------------------------------------------------ effects

    private void drawTargetRing(Location centre) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        double radius = Math.max(1.0D, config.spread());
        int points = (int) Math.max(24.0D, Math.min(240.0D, radius * 4.0D));
        Location ringCentre = centre.clone().add(0.0D, 0.1D, 0.0D);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !world.isChunkLoaded(ringCentre.getBlockX() >> 4, ringCentre.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }
                ticks++;
                for (int point = 0; point < points; point++) {
                    double angle = (Math.PI * 2.0D / points) * point;
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

    // ------------------------------------------------------------------ skull identity

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

    /** VANILLA mode only: replaces a skull's blast with a configured explosion. */
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

    // ------------------------------------------------------------------ block key packing

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackY(long key) {
        return (int) (key << 26 >> 52);
    }

    private static int unpackZ(long key) {
        return (int) (key << 38 >> 38);
    }
}
