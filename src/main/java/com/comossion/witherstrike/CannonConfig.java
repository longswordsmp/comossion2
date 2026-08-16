package com.comossion.witherstrike;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed, clamped view over config.yml. Every value is re-read on {@link #reload()}
 * so /witherstrikecannon reload takes effect without a restart.
 */
public final class CannonConfig {

    public enum TargetingMode {
        BOBBER,
        RAYTRACE
    }

    public enum ExplosionMode {
        /** We carve the crater ourselves - far more destruction, and nothing knocks the skulls around. */
        CUSTOM,
        /** Let the wither skulls explode normally. */
        VANILLA
    }

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private TargetingMode targetingMode = TargetingMode.RAYTRACE;
    private double maxDistance = 300.0D;
    private int hookTimeoutTicks = 120;

    private int skullCount = 2000;
    private double spread = 45.0D;
    private double impactJitter = 3.0D;
    private double height = 140.0D;
    private int waves = 100;
    private int waveDelayTicks = 1;
    private boolean charged = false;
    private double speedMultiplier = 1.0D;
    private boolean invulnerable = true;
    private int cooldownSeconds = 30;
    private boolean protectShooter = true;
    private boolean effects = true;
    private int impactEffectEvery = 3;
    private Set<String> disabledWorlds = new HashSet<>();

    private ExplosionMode explosionMode = ExplosionMode.CUSTOM;
    private double blastRadius = 10.0D;
    private double blastDepth = 4.0D;
    private double blastHeight = 12.0D;
    private int blocksPerTick = 5000;
    private int maxBlocksPerStrike = 500_000;
    private boolean carveLiquids = false;
    private double damagePerPulse = 10.0D;
    private int damagePulses = 10;
    private int damagePulseIntervalTicks = 10;
    private boolean blockDamage = true;
    private double explosionPower = 4.0D;
    private boolean incendiary = false;

    private boolean itemGlint = true;
    private boolean itemUnbreakable = true;

    public CannonConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        String mode = config.getString("targeting.mode", "RAYTRACE");
        try {
            targetingMode = TargetingMode.valueOf(String.valueOf(mode).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown targeting.mode '" + mode + "', falling back to RAYTRACE.");
            targetingMode = TargetingMode.RAYTRACE;
        }
        maxDistance = clamp(config.getDouble("targeting.max-distance", 300.0D), 1.0D, 512.0D);
        hookTimeoutTicks = (int) clamp(config.getInt("targeting.hook-timeout-ticks", 120), 20, 1200);

        int maxSkulls = (int) clamp(config.getInt("strike.max-skull-count", 5000), 1, 20_000);
        skullCount = (int) clamp(config.getInt("strike.skull-count", 2000), 1, maxSkulls);
        spread = clamp(config.getDouble("strike.spread", 45.0D), 0.0D, 256.0D);
        impactJitter = clamp(config.getDouble("strike.impact-jitter", 3.0D), 0.0D, 64.0D);
        height = clamp(config.getDouble("strike.height", 140.0D), 1.0D, 320.0D);
        waves = (int) clamp(config.getInt("strike.waves", 100), 1, skullCount);
        waveDelayTicks = (int) clamp(config.getInt("strike.wave-delay-ticks", 1), 0, 100);
        charged = config.getBoolean("strike.charged", false);
        speedMultiplier = clamp(config.getDouble("strike.speed-multiplier", 1.0D), 0.05D, 20.0D);
        invulnerable = config.getBoolean("strike.invulnerable", true);
        cooldownSeconds = (int) clamp(config.getInt("strike.cooldown-seconds", 30), 0, 3600);
        protectShooter = config.getBoolean("strike.protect-shooter", true);
        effects = config.getBoolean("strike.effects", true);
        impactEffectEvery = (int) clamp(config.getInt("strike.impact-effect-every", 3), 1, 1000);

        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("strike.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        disabledWorlds = worlds;

        String explosion = config.getString("explosion.mode", "CUSTOM");
        try {
            explosionMode = ExplosionMode.valueOf(String.valueOf(explosion).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown explosion.mode '" + explosion + "', falling back to CUSTOM.");
            explosionMode = ExplosionMode.CUSTOM;
        }
        blastRadius = clamp(config.getDouble("explosion.blast-radius", 10.0D), 1.0D, 64.0D);
        blastDepth = clamp(config.getDouble("explosion.blast-depth", 4.0D), 0.0D, 64.0D);
        blastHeight = clamp(config.getDouble("explosion.blast-height", 12.0D), 0.0D, 64.0D);
        blocksPerTick = (int) clamp(config.getInt("explosion.blocks-per-tick", 5000), 100, 200_000);
        maxBlocksPerStrike = (int) clamp(config.getInt("explosion.max-blocks-per-strike", 500_000), 1000, 20_000_000);
        carveLiquids = config.getBoolean("explosion.carve-liquids", false);
        damagePerPulse = clamp(config.getDouble("explosion.damage-per-pulse", 10.0D), 0.0D, 2048.0D);
        damagePulses = (int) clamp(config.getInt("explosion.damage-pulses", 10), 0, 200);
        damagePulseIntervalTicks = (int) clamp(config.getInt("explosion.damage-pulse-interval-ticks", 10), 1, 200);
        blockDamage = config.getBoolean("explosion.block-damage", true);
        explosionPower = clamp(config.getDouble("explosion.power", 4.0D), -1.0D, 50.0D);
        incendiary = config.getBoolean("explosion.incendiary", false);

        itemGlint = config.getBoolean("item.glint", true);
        itemUnbreakable = config.getBoolean("item.unbreakable", true);
    }

    public TargetingMode targetingMode() {
        return targetingMode;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public int hookTimeoutTicks() {
        return hookTimeoutTicks;
    }

    public int skullCount() {
        return skullCount;
    }

    public double spread() {
        return spread;
    }

    public double impactJitter() {
        return impactJitter;
    }

    public double height() {
        return height;
    }

    public int waves() {
        return waves;
    }

    public int waveDelayTicks() {
        return waveDelayTicks;
    }

    public boolean charged() {
        return charged;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public boolean invulnerable() {
        return invulnerable;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean protectShooter() {
        return protectShooter;
    }

    public boolean effects() {
        return effects;
    }

    public int impactEffectEvery() {
        return impactEffectEvery;
    }

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public ExplosionMode explosionMode() {
        return explosionMode;
    }

    /** Horizontal radius of each impact. */
    public double blastRadius() {
        return blastRadius;
    }

    /** How far below the impact point the carve reaches - keep this small or craters get bottomless. */
    public double blastDepth() {
        return blastDepth;
    }

    /** How far above the impact point the carve reaches, so structures still get flattened. */
    public double blastHeight() {
        return blastHeight;
    }

    public int blocksPerTick() {
        return blocksPerTick;
    }

    public int maxBlocksPerStrike() {
        return maxBlocksPerStrike;
    }

    public boolean carveLiquids() {
        return carveLiquids;
    }

    public double damagePerPulse() {
        return damagePerPulse;
    }

    public int damagePulses() {
        return damagePulses;
    }

    public int damagePulseIntervalTicks() {
        return damagePulseIntervalTicks;
    }

    public boolean blockDamage() {
        return blockDamage;
    }

    /** Explosion power override, or a negative value to keep the vanilla wither skull blast. */
    public double explosionPower() {
        return explosionPower;
    }

    public boolean incendiary() {
        return incendiary;
    }

    public boolean itemGlint() {
        return itemGlint;
    }

    public boolean itemUnbreakable() {
        return itemUnbreakable;
    }

    public Component itemName() {
        String raw = plugin.getConfig().getString("item.name", "<light_purple>Wither Strike Cannon");
        if (raw == null) {
            raw = "<light_purple>Wither Strike Cannon";
        }
        return miniMessage.deserialize(raw).decoration(TextDecoration.ITALIC, false);
    }

    public List<Component> itemLore() {
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("item.lore")) {
            lore.add(miniMessage.deserialize(line == null ? "" : line).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    /**
     * Builds a prefixed, MiniMessage formatted chat message.
     *
     * @param key           message key under {@code messages.}
     * @param replacements  alternating placeholder/value pairs, e.g. {@code "%player%", name}
     */
    public Component message(String key, String... replacements) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return miniMessage.deserialize((prefix == null ? "" : prefix) + raw);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
