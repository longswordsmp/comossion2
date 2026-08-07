package com.longswordsmp.nolife.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads and writes {@code data.yml} which holds every tracked player and any
 * pending revives for offline players.
 */
public class DataStore {

    private final JavaPlugin plugin;
    private final File file;

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, PendingRevive> pending = new HashMap<>();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        pending.clear();

        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection playerSection = yaml.getConfigurationSection("players");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection s = playerSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                String name = s.getString("name", "");
                int lives = s.getInt("lives", 3);
                boolean eliminated = s.getBoolean("eliminated", false);
                players.put(uuid, new PlayerData(uuid, name, lives, eliminated));
            }
        }

        ConfigurationSection pendingSection = yaml.getConfigurationSection("pending-revives");
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection s = pendingSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                pending.put(uuid, new PendingRevive(
                        s.getString("world", "world"),
                        s.getDouble("x"),
                        s.getDouble("y"),
                        s.getDouble("z"),
                        (float) s.getDouble("yaw"),
                        (float) s.getDouble("pitch")));
            }
        }
    }

    public void save() {
        FileConfiguration yaml = new YamlConfiguration();

        for (PlayerData data : players.values()) {
            String base = "players." + data.getUuid();
            yaml.set(base + ".name", data.getName());
            yaml.set(base + ".lives", data.getLives());
            yaml.set(base + ".eliminated", data.isEliminated());
        }

        for (Map.Entry<UUID, PendingRevive> entry : pending.entrySet()) {
            PendingRevive p = entry.getValue();
            String base = "pending-revives." + entry.getKey();
            yaml.set(base + ".world", p.world());
            yaml.set(base + ".x", p.x());
            yaml.set(base + ".y", p.y());
            yaml.set(base + ".z", p.z());
            yaml.set(base + ".yaw", p.yaw());
            yaml.set(base + ".pitch", p.pitch());
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    // ---- player data ------------------------------------------------------

    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    public void put(PlayerData data) {
        players.put(data.getUuid(), data);
    }

    public Collection<PlayerData> all() {
        return players.values();
    }

    /** Find a tracked player by (case-insensitive) name. */
    public PlayerData getByName(String name) {
        if (name == null) {
            return null;
        }
        for (PlayerData data : players.values()) {
            if (name.equalsIgnoreCase(data.getName())) {
                return data;
            }
        }
        return null;
    }

    public List<PlayerData> getEliminatedSorted() {
        List<PlayerData> out = new ArrayList<>();
        for (PlayerData data : players.values()) {
            if (data.isEliminated()) {
                out.add(data);
            }
        }
        out.sort((a, b) -> {
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return an.toLowerCase(Locale.ROOT).compareTo(bn.toLowerCase(Locale.ROOT));
        });
        return out;
    }

    // ---- pending revives --------------------------------------------------

    public PendingRevive getPending(UUID uuid) {
        return pending.get(uuid);
    }

    public void putPending(UUID uuid, PendingRevive revive) {
        pending.put(uuid, revive);
    }

    public void removePending(UUID uuid) {
        pending.remove(uuid);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
