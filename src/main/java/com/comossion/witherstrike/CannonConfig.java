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

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private TargetingMode targetingMode = TargetingMode.BOBBER;
    private double maxDistance = 128.0D;
    private int hookTimeoutTicks = 120;

    private int skullCount = 150;
    private double spread = 1.5D;
    private double height = 80.0D;
    private int waves = 10;
    private int waveDelayTicks = 1;
    private boolean scatterImpacts = true;
    private boolean charged = false;
    private double speedMultiplier = 1.0D;
    private boolean invulnerable = true;
    private int cooldownSeconds = 5;
    private boolean protectShooter = true;
    private boolean effects = true;
    private Set<String> disabledWorlds = new HashSet<>();

    private boolean blockDamage = true;
    private double explosionPower = -1.0D;
    private boolean incendiary = false;

    private boolean itemGlint = true;
    private boolean itemUnbreakable = true;

    public CannonConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        String mode = config.getString("targeting.mode", "BOBBER");
        try {
            targetingMode = TargetingMode.valueOf(String.valueOf(mode).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown targeting.mode '" + mode + "', falling back to BOBBER.");
            targetingMode = TargetingMode.BOBBER;
        }
        maxDistance = clamp(config.getDouble("targeting.max-distance", 128.0D), 1.0D, 512.0D);
        hookTimeoutTicks = (int) clamp(config.getInt("targeting.hook-timeout-ticks", 120), 20, 1200);

        int maxSkulls = (int) clamp(config.getInt("strike.max-skull-count", 1000), 1, 10_000);
        skullCount = (int) clamp(config.getInt("strike.skull-count", 150), 1, maxSkulls);
        spread = clamp(config.getDouble("strike.spread", 1.5D), 0.0D, 64.0D);
        height = clamp(config.getDouble("strike.height", 80.0D), 1.0D, 320.0D);
        waves = (int) clamp(config.getInt("strike.waves", 10), 1, skullCount);
        waveDelayTicks = (int) clamp(config.getInt("strike.wave-delay-ticks", 1), 0, 100);
        scatterImpacts = config.getBoolean("strike.scatter-impacts", true);
        charged = config.getBoolean("strike.charged", false);
        speedMultiplier = clamp(config.getDouble("strike.speed-multiplier", 1.0D), 0.05D, 20.0D);
        invulnerable = config.getBoolean("strike.invulnerable", true);
        cooldownSeconds = (int) clamp(config.getInt("strike.cooldown-seconds", 5), 0, 3600);
        protectShooter = config.getBoolean("strike.protect-shooter", true);
        effects = config.getBoolean("strike.effects", true);

        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("strike.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        disabledWorlds = worlds;

        blockDamage = config.getBoolean("explosion.block-damage", true);
        explosionPower = clamp(config.getDouble("explosion.power", -1.0D), -1.0D, 50.0D);
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

    public double height() {
        return height;
    }

    public int waves() {
        return waves;
    }

    public int waveDelayTicks() {
        return waveDelayTicks;
    }

    public boolean scatterImpacts() {
        return scatterImpacts;
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

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
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
