package com.longswordsmp.nolife.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.data.PlayerData;

/**
 * Implements /setlives, /revive, /eliminate and /nlreload (all gated behind
 * {@code nolife.admin}) plus their tab completion.
 */
public class AdminCommands implements CommandExecutor, TabCompleter {

    private final NoLifePlugin plugin;

    public AdminCommands(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    private PluginConfig cfg() {
        return plugin.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nolife.admin")) {
            sender.sendMessage(cfg().msg("no-permission"));
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "setlives":
                return setLives(sender, args);
            case "revive":
                return revive(sender, args);
            case "eliminate":
                return eliminate(sender, args);
            case "nlreload":
                plugin.reloadAll();
                sender.sendMessage(cfg().msg("reloaded", "%version%", cfg().resourcePackVersion()));
                return true;
            default:
                return false;
        }
    }

    private boolean setLives(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(cfg().msg("usage-setlives"));
            return true;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(cfg().msg("invalid-number", "%amount%", args[1]));
            return true;
        }

        String name = displayName(id, args[0]);
        if (amount <= 0) {
            plugin.lives().eliminate(id, name, true);
            sender.sendMessage(cfg().msg("eliminate-done", "%player%", name));
        } else {
            int applied = plugin.lives().setLives(id, amount, name);
            sender.sendMessage(cfg().msg("setlives-done", "%player%", name, "%lives%", String.valueOf(applied)));
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                online.sendMessage(cfg().msg("setlives-target", "%lives%", String.valueOf(applied)));
            }
        }
        return true;
    }

    private boolean revive(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(cfg().msg("usage-revive"));
            return true;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return true;
        }
        String name = displayName(id, args[0]);
        if (!plugin.lives().isEliminated(id)) {
            sender.sendMessage(cfg().msg("revive-not-eliminated", "%player%", name));
            return true;
        }

        Location loc;
        String reviverName;
        if (sender instanceof Player player) {
            loc = player.getLocation();
            reviverName = player.getName();
        } else {
            loc = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
            reviverName = "Console";
        }
        plugin.lives().revive(id, loc, reviverName);
        sender.sendMessage(cfg().msg("revive-done", "%player%", name));
        return true;
    }

    private boolean eliminate(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(cfg().msg("usage-eliminate"));
            return true;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return true;
        }
        String name = displayName(id, args[0]);
        if (plugin.lives().isEliminated(id)) {
            sender.sendMessage(cfg().msg("eliminate-already", "%player%", name));
            return true;
        }
        plugin.lives().eliminate(id, name, true);
        sender.sendMessage(cfg().msg("eliminate-done", "%player%", name));
        return true;
    }

    // ---- helpers ----------------------------------------------------------

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        PlayerData data = plugin.data().getByName(name);
        return data != null ? data.getUuid() : null;
    }

    private String displayName(UUID id, String fallback) {
        PlayerData data = plugin.data().get(id);
        if (data != null && data.getName() != null) {
            return data.getName();
        }
        Player online = Bukkit.getPlayer(id);
        return online != null ? online.getName() : fallback;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nolife.admin")) {
            return Collections.emptyList();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            for (PlayerData data : plugin.data().all()) {
                if (data.getName() != null) {
                    names.add(data.getName());
                }
            }
            List<String> out = new ArrayList<>();
            for (String candidate : names) {
                if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(candidate);
                }
            }
            return out;
        }

        if (name.equals("setlives") && args.length == 2) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i <= plugin.config().maxLives(); i++) {
                String value = String.valueOf(i);
                if (value.startsWith(args[1])) {
                    out.add(value);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
