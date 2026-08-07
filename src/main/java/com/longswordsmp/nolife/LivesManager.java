package com.longswordsmp.nolife;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.data.DataStore;
import com.longswordsmp.nolife.data.PendingRevive;
import com.longswordsmp.nolife.data.PlayerData;

/**
 * The heart of the plugin: tracks lives, keeps the name-tag / tab-list colour
 * in sync via scoreboard teams, and handles elimination and revival.
 */
public class LivesManager {

    private static final String TEAM_THREE = "nl_three";
    private static final String TEAM_TWO = "nl_two";
    private static final String TEAM_ONE = "nl_one";

    private final JavaPlugin plugin;
    private final PluginConfig cfg;
    private final DataStore data;

    private Team teamThree;
    private Team teamTwo;
    private Team teamOne;

    private Particle totemParticle;

    public LivesManager(JavaPlugin plugin, PluginConfig cfg, DataStore data) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.data = data;
    }

    // ---- teams / display --------------------------------------------------

    public void setupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        teamThree = getOrCreateTeam(board, TEAM_THREE);
        teamTwo = getOrCreateTeam(board, TEAM_TWO);
        teamOne = getOrCreateTeam(board, TEAM_ONE);
        clearEntries(teamThree);
        clearEntries(teamTwo);
        clearEntries(teamOne);
        applyColors();
    }

    /** Re-apply the (possibly changed) colours from config to each team. */
    public void applyColors() {
        if (teamThree != null) {
            teamThree.setColor(cfg.colorThree());
        }
        if (teamTwo != null) {
            teamTwo.setColor(cfg.colorTwo());
        }
        if (teamOne != null) {
            teamOne.setColor(cfg.colorOne());
        }
    }

    private Team getOrCreateTeam(Scoreboard board, String name) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        return team;
    }

    private void clearEntries(Team team) {
        for (String entry : new HashSet<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    private void removeFromAllTeams(String entry) {
        if (teamThree != null) {
            teamThree.removeEntry(entry);
        }
        if (teamTwo != null) {
            teamTwo.removeEntry(entry);
        }
        if (teamOne != null) {
            teamOne.removeEntry(entry);
        }
    }

    /** Recolour a player's name-tag and tab-list entry for their life count. */
    public void updateDisplay(Player player) {
        String entry = player.getName();
        removeFromAllTeams(entry);

        if (isEliminated(player.getUniqueId())) {
            return;
        }
        int lives = getLivesOrDefault(player.getUniqueId());
        Team target = null;
        if (lives >= 3) {
            target = teamThree;
        } else if (lives == 2) {
            target = teamTwo;
        } else if (lives == 1) {
            target = teamOne;
        }
        if (target != null) {
            target.addEntry(entry);
        }
    }

    public void refreshOnlineDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureData(player);
            updateDisplay(player);
        }
    }

    // ---- lives ------------------------------------------------------------

    public int getLivesOrDefault(UUID uuid) {
        PlayerData d = data.get(uuid);
        return d != null ? d.getLives() : cfg.startingLives();
    }

    public boolean isEliminated(UUID uuid) {
        PlayerData d = data.get(uuid);
        return d != null && d.isEliminated();
    }

    /** Ensure a data row exists for a player who just joined and refresh their name. */
    public PlayerData ensureData(Player player) {
        PlayerData d = data.get(player.getUniqueId());
        if (d == null) {
            d = new PlayerData(player.getUniqueId(), player.getName(), cfg.startingLives(), false);
            data.put(d);
            data.save();
        } else if (!player.getName().equals(d.getName())) {
            d.setName(player.getName());
            data.save();
        }
        return d;
    }

    private PlayerData getOrCreate(UUID uuid, String nameHint) {
        PlayerData d = data.get(uuid);
        if (d == null) {
            String name = nameHint;
            if (name == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                name = op.getName();
            }
            if (name == null) {
                name = uuid.toString();
            }
            d = new PlayerData(uuid, name, cfg.startingLives(), false);
            data.put(d);
        }
        return d;
    }

    /**
     * Set a player's lives (clamped to 0..max). A value above 0 also clears the
     * eliminated flag. Returns the value actually applied.
     */
    public int setLives(UUID uuid, int lives, String nameHint) {
        int value = cfg.clamp(lives);
        PlayerData d = getOrCreate(uuid, nameHint);
        d.setLives(value);
        if (value > 0) {
            d.setEliminated(false);
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            updateDisplay(online);
        }
        data.save();
        return value;
    }

    // ---- elimination / revival -------------------------------------------

    /** Death-ban a player, optionally broadcasting, and kick them if online. */
    public void eliminate(UUID uuid, String nameHint, boolean announce) {
        PlayerData d = getOrCreate(uuid, nameHint);
        d.setEliminated(true);
        d.setLives(0);
        String name = d.getName();

        Player online = Bukkit.getPlayer(uuid);
        removeFromAllTeams(online != null ? online.getName() : name);

        if (announce) {
            broadcast(cfg.msg("broadcast.death", "%player%", name));
        }
        data.save();

        if (online != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player o = Bukkit.getPlayer(uuid);
                // Re-check eliminated so an admin revive during the delay cancels the kick.
                if (o != null && o.isOnline() && isEliminated(uuid)) {
                    o.kick(cfg.msg("ban-kick"));
                }
            }, cfg.kickDelayTicks());
        }
    }

    /**
     * Revive a player: clear the ban, set revive-lives, teleport to {@code loc}
     * (now if online, otherwise when they next join) and play totem effects.
     */
    public void revive(UUID uuid, Location loc, String reviverName) {
        PlayerData d = getOrCreate(uuid, null);
        d.setEliminated(false);
        d.setLives(cfg.reviveLives());
        String name = d.getName();

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            if (loc != null) {
                online.teleport(loc);
            }
            online.setGameMode(GameMode.SURVIVAL);
            updateDisplay(online);
            playReviveEffects(online);
            data.removePending(uuid);
        } else if (loc != null) {
            data.putPending(uuid, PendingRevive.of(loc));
        }

        broadcast(cfg.msg("broadcast.revive", "%player%", name, "%reviver%", reviverName));
        data.save();
    }

    /** Called on join: if a revive is pending, teleport there and play effects. */
    public void applyPendingRevive(Player player) {
        UUID uuid = player.getUniqueId();
        PendingRevive pending = data.getPending(uuid);
        if (pending == null) {
            return;
        }
        data.removePending(uuid);
        data.save();

        Location loc = pending.toLocation();
        // Run next tick so the player is fully in the world before teleporting.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (loc != null) {
                player.teleport(loc);
            }
            player.setGameMode(GameMode.SURVIVAL);
            playReviveEffects(player);
        });
    }

    public void playReviveEffects(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, "minecraft:item.totem.use", 1.0f, 1.0f);
        world.spawnParticle(totemParticle(), loc.clone().add(0.0, 1.0, 0.0), 60, 0.5, 1.0, 0.5, 0.15);
    }

    private Particle totemParticle() {
        if (totemParticle == null) {
            Particle resolved = Particle.HEART; // guaranteed fallback
            for (String name : new String[]{"TOTEM_OF_UNDYING", "TOTEM"}) {
                try {
                    resolved = Particle.valueOf(name);
                    break;
                } catch (IllegalArgumentException ignored) {
                    // try the next known name
                }
            }
            totemParticle = resolved;
        }
        return totemParticle;
    }

    // ---- helpers ----------------------------------------------------------

    public List<PlayerData> getEliminatedSorted() {
        return data.getEliminatedSorted();
    }

    public DataStore data() {
        return data;
    }

    private void broadcast(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }
}
