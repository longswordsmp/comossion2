package com.longswordsmp.nolife.bounty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tracks all active bounties and persists them to {@code bounties.yml}.
 *
 * <p>A bounty is keyed by the target's UUID and records each placer's staked
 * Life Gems, so an individual placer can reclaim exactly what they put in while
 * a killer collects the whole pot.</p>
 */
public class BountyManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Bounty> bounties = new HashMap<>();

    public BountyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
    }

    // ---- persistence ------------------------------------------------------

    public void load() {
        bounties.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("bounties");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID target = parseUuid(key);
            if (target == null) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Bounty bounty = new Bounty(target, s.getString("name", key));
            ConfigurationSection placers = s.getConfigurationSection("placers");
            if (placers != null) {
                for (String pk : placers.getKeys(false)) {
                    UUID placer = parseUuid(pk);
                    if (placer == null) {
                        continue;
                    }
                    ConfigurationSection ps = placers.getConfigurationSection(pk);
                    if (ps == null) {
                        continue;
                    }
                    int amount = ps.getInt("amount", 0);
                    if (amount > 0) {
                        bounty.add(placer, ps.getString("name", pk), amount);
                    }
                }
            }
            if (!bounty.isEmpty()) {
                bounties.put(target, bounty);
            }
        }
    }

    public void save() {
        FileConfiguration yaml = new YamlConfiguration();
        for (Bounty bounty : bounties.values()) {
            String base = "bounties." + bounty.target();
            yaml.set(base + ".name", bounty.targetName());
            for (Map.Entry<UUID, Integer> entry : bounty.contributions().entrySet()) {
                String pbase = base + ".placers." + entry.getKey();
                yaml.set(pbase + ".name", bounty.placerName(entry.getKey()));
                yaml.set(pbase + ".amount", entry.getValue());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bounties.yml", e);
        }
    }

    // ---- queries ----------------------------------------------------------

    public Bounty get(UUID target) {
        return bounties.get(target);
    }

    public int total(UUID target) {
        Bounty b = bounties.get(target);
        return b == null ? 0 : b.total();
    }

    public int contribution(UUID target, UUID placer) {
        Bounty b = bounties.get(target);
        return b == null ? 0 : b.contribution(placer);
    }

    public boolean isEmpty() {
        return bounties.isEmpty();
    }

    /** Active bounties, richest pot first. */
    public List<Bounty> allSorted() {
        List<Bounty> out = new ArrayList<>(bounties.values());
        out.sort(Comparator.comparingInt(Bounty::total).reversed());
        return out;
    }

    // ---- mutations --------------------------------------------------------

    /** Stake gems on a target (creating the bounty if needed); returns new total. */
    public int add(UUID target, String targetName, UUID placer, String placerName, int amount) {
        Bounty b = bounties.computeIfAbsent(target, t -> new Bounty(t, targetName));
        b.setTargetName(targetName);
        b.add(placer, placerName, amount);
        save();
        return b.total();
    }

    /** Reclaim a placer's stake; returns the refunded amount (0 if none). */
    public int removeContribution(UUID target, UUID placer) {
        Bounty b = bounties.get(target);
        if (b == null) {
            return 0;
        }
        int refunded = b.removeContribution(placer);
        if (b.isEmpty()) {
            bounties.remove(target);
        }
        if (refunded > 0) {
            save();
        }
        return refunded;
    }

    /** Remove a target's whole bounty (a kill payout) and return its total. */
    public int claimAll(UUID target) {
        Bounty b = bounties.remove(target);
        if (b == null) {
            return 0;
        }
        save();
        return b.total();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
