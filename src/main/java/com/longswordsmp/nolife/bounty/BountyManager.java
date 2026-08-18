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

    /** Whether a server-wide bounty event is currently running. */
    private boolean eventActive = false;
    /** Gems owed to placers who were offline when a bounty event ended. */
    private final Map<UUID, Integer> pendingRefunds = new HashMap<>();

    public BountyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
    }

    // ---- persistence ------------------------------------------------------

    public void load() {
        bounties.clear();
        pendingRefunds.clear();
        eventActive = false;
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        eventActive = yaml.getBoolean("event-active", false);

        ConfigurationSection root = yaml.getConfigurationSection("bounties");
        if (root != null) {
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

        ConfigurationSection refunds = yaml.getConfigurationSection("pending-refunds");
        if (refunds != null) {
            for (String pk : refunds.getKeys(false)) {
                UUID placer = parseUuid(pk);
                if (placer == null) {
                    continue;
                }
                int amount = refunds.getInt(pk, 0);
                if (amount > 0) {
                    pendingRefunds.put(placer, amount);
                }
            }
        }
    }

    public void save() {
        FileConfiguration yaml = new YamlConfiguration();
        yaml.set("event-active", eventActive);
        for (Bounty bounty : bounties.values()) {
            String base = "bounties." + bounty.target();
            yaml.set(base + ".name", bounty.targetName());
            for (Map.Entry<UUID, Integer> entry : bounty.contributions().entrySet()) {
                String pbase = base + ".placers." + entry.getKey();
                yaml.set(pbase + ".name", bounty.placerName(entry.getKey()));
                yaml.set(pbase + ".amount", entry.getValue());
            }
        }
        for (Map.Entry<UUID, Integer> entry : pendingRefunds.entrySet()) {
            yaml.set("pending-refunds." + entry.getKey(), entry.getValue());
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

    // ---- bounty event -----------------------------------------------------

    public boolean isEventActive() {
        return eventActive;
    }

    public void setEventActive(boolean active) {
        this.eventActive = active;
        save();
    }

    /**
     * Clear every active bounty and return the total gems owed back to each
     * placer (aggregated across all bounties). Used when a bounty event ends.
     */
    public Map<UUID, Integer> clearAllAndComputeRefunds() {
        Map<UUID, Integer> refunds = new HashMap<>();
        for (Bounty bounty : bounties.values()) {
            for (Map.Entry<UUID, Integer> entry : bounty.contributions().entrySet()) {
                refunds.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        bounties.clear();
        save();
        return refunds;
    }

    // ---- pending refunds (placer was offline when an event ended) ---------

    public void addPendingRefund(UUID placer, int amount) {
        if (amount <= 0) {
            return;
        }
        pendingRefunds.merge(placer, amount, Integer::sum);
        save();
    }

    /** Remove and return a placer's queued refund (0 if none). */
    public int takePendingRefund(UUID placer) {
        Integer amount = pendingRefunds.remove(placer);
        if (amount == null || amount <= 0) {
            return 0;
        }
        save();
        return amount;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
