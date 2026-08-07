package com.longswordsmp.nolife.commands;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.bukkit.inventory.ItemStack;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.data.PlayerData;
import com.longswordsmp.nolife.gui.Guis;

/**
 * All NoLife commands. Admin commands (setlives / revive / eliminate / nlgive /
 * nlreload / nolife) require {@code nolife.admin}; /lives and /nlrecipes are
 * usable by everyone (own lives / recipe book).
 */
public class AdminCommands implements CommandExecutor, TabCompleter {

    private static final List<String> ITEM_KEYWORDS = Arrays.asList("book", "gem");

    private final NoLifePlugin plugin;

    public AdminCommands(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    private PluginConfig cfg() {
        return plugin.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "nolife":
                if (admin(sender)) {
                    hub(sender);
                }
                return true;
            case "setlives":
                if (admin(sender)) {
                    setLives(sender, args);
                }
                return true;
            case "revive":
                if (admin(sender)) {
                    revive(sender, args);
                }
                return true;
            case "eliminate":
                if (admin(sender)) {
                    eliminate(sender, args);
                }
                return true;
            case "nlgive":
                if (admin(sender)) {
                    give(sender, args);
                }
                return true;
            case "nlreload":
                if (admin(sender)) {
                    plugin.reloadAll();
                    sender.sendMessage(cfg().msg("reloaded", "%version%", cfg().resourcePackVersion()));
                }
                return true;
            case "lives":
                return lives(sender, args);
            case "nlrecipes":
                return recipes(sender);
            default:
                return false;
        }
    }

    private boolean admin(CommandSender sender) {
        if (!sender.hasPermission("nolife.admin")) {
            sender.sendMessage(cfg().msg("no-permission"));
            return false;
        }
        return true;
    }

    private void hub(CommandSender sender) {
        if (sender instanceof Player player) {
            Guis.openAdminHub(plugin, player);
        } else {
            sender.sendMessage(cfg().msg("players-only"));
        }
    }

    // ---- /setlives --------------------------------------------------------

    private void setLives(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(cfg().msg("usage-setlives"));
            return;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(cfg().msg("invalid-number", "%amount%", args[1]));
            return;
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
    }

    // ---- /revive ----------------------------------------------------------

    private void revive(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(cfg().msg("usage-revive"));
            return;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return;
        }
        String name = displayName(id, args[0]);
        if (!plugin.lives().isEliminated(id)) {
            sender.sendMessage(cfg().msg("revive-not-eliminated", "%player%", name));
            return;
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
    }

    // ---- /eliminate -------------------------------------------------------

    private void eliminate(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(cfg().msg("usage-eliminate"));
            return;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
            return;
        }
        String name = displayName(id, args[0]);
        if (plugin.lives().isEliminated(id)) {
            sender.sendMessage(cfg().msg("eliminate-already", "%player%", name));
            return;
        }
        plugin.lives().eliminate(id, name, true);
        sender.sendMessage(cfg().msg("eliminate-done", "%player%", name));
    }

    // ---- /nlgive ----------------------------------------------------------

    private void give(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(cfg().msg("usage-nlgive"));
            return;
        }

        Player target;
        String itemKey;
        int amount;

        String selfKey = itemKeyword(args[0]);
        if (selfKey != null) {
            // /nlgive <item> [amount]  -> give to self
            if (!(sender instanceof Player player)) {
                sender.sendMessage(cfg().msg("usage-nlgive"));
                return;
            }
            target = player;
            itemKey = selfKey;
            amount = args.length >= 2 ? parseAmount(sender, args[1]) : 1;
        } else {
            // /nlgive <player> <item> [amount]
            if (args.length < 2) {
                sender.sendMessage(cfg().msg("usage-nlgive"));
                return;
            }
            itemKey = itemKeyword(args[1]);
            if (itemKey == null) {
                sender.sendMessage(cfg().msg("give-unknown-item", "%item%", args[1]));
                return;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(cfg().msg("give-offline", "%player%", args[0]));
                return;
            }
            amount = args.length >= 3 ? parseAmount(sender, args[2]) : 1;
        }

        if (amount < 1) {
            return; // parseAmount already sent the error
        }

        boolean book = itemKey.equals("book");
        ItemStack item = book ? plugin.items().createBookOfLife(amount) : plugin.items().createLifeGem(amount);
        String label = book ? "Book of Life" : "Life Gem";
        plugin.items().give(target, item);

        boolean self = (sender instanceof Player sp) && sp.getUniqueId().equals(target.getUniqueId());
        target.sendMessage(cfg().msg("give-received", "%amount%", String.valueOf(amount), "%item%", label));
        if (!self) {
            sender.sendMessage(cfg().msg("give-sent", "%player%", target.getName(),
                    "%amount%", String.valueOf(amount), "%item%", label));
        }
    }

    private int parseAmount(CommandSender sender, String raw) {
        try {
            int n = Integer.parseInt(raw);
            return Math.max(1, Math.min(64, n));
        } catch (NumberFormatException e) {
            sender.sendMessage(cfg().msg("invalid-number", "%amount%", raw));
            return -1;
        }
    }

    private String itemKeyword(String s) {
        String k = s.toLowerCase(Locale.ROOT);
        if (k.equals("book") || k.equals("bookoflife") || k.equals("book_of_life") || k.equals("bol")) {
            return "book";
        }
        if (k.equals("gem") || k.equals("life") || k.equals("lifegem") || k.equals("life_gem") || k.equals("lg")) {
            return "gem";
        }
        return null;
    }

    // ---- /lives -----------------------------------------------------------

    private boolean lives(CommandSender sender, String[] args) {
        UUID id;
        String name;
        if (args.length == 0) {
            if (!sender.hasPermission("nolife.lives")) {
                sender.sendMessage(cfg().msg("no-permission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(cfg().msg("usage-lives"));
                return true;
            }
            id = player.getUniqueId();
            name = player.getName();
        } else {
            if (!sender.hasPermission("nolife.admin")) {
                sender.sendMessage(cfg().msg("no-permission"));
                return true;
            }
            id = resolve(args[0]);
            if (id == null) {
                sender.sendMessage(cfg().msg("unknown-player", "%player%", args[0]));
                return true;
            }
            name = displayName(id, args[0]);
        }

        if (sender instanceof Player viewer) {
            Guis.openLives(plugin, viewer, id, name);
        } else {
            boolean elim = plugin.lives().isEliminated(id);
            int lv = elim ? 0 : plugin.lives().getLivesOrDefault(id);
            sender.sendMessage(cfg().msg("lives-text", "%player%", name,
                    "%lives%", String.valueOf(lv), "%max%", String.valueOf(plugin.config().maxLives())));
        }
        return true;
    }

    // ---- /nlrecipes -------------------------------------------------------

    private boolean recipes(CommandSender sender) {
        if (!sender.hasPermission("nolife.recipes")) {
            sender.sendMessage(cfg().msg("no-permission"));
            return true;
        }
        if (sender instanceof Player player) {
            Guis.openRecipes(plugin, player);
        } else {
            sender.sendMessage(cfg().msg("players-only"));
        }
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

    private List<String> onlineAndTrackedNames(String prefix) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (PlayerData data : plugin.data().all()) {
            if (data.getName() != null) {
                names.add(data.getName());
            }
        }
        return matching(names, prefix);
    }

    /** Only currently-online player names (used where offline targets are rejected). */
    private List<String> onlineNames(String prefix) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return matching(names, prefix);
    }

    private List<String> matching(TreeSet<String> names, String prefix) {
        List<String> out = new ArrayList<>();
        for (String candidate : names) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(candidate);
            }
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean isAdmin = sender.hasPermission("nolife.admin");

        if (name.equals("lives")) {
            if (args.length == 1 && isAdmin) {
                return onlineAndTrackedNames(args[0].toLowerCase(Locale.ROOT));
            }
            return Collections.emptyList();
        }

        if (name.equals("nolife") || name.equals("nlrecipes") || name.equals("nlreload")) {
            return Collections.emptyList();
        }

        if (!isAdmin) {
            return Collections.emptyList();
        }

        if (name.equals("nlgive")) {
            if (args.length == 1) {
                // /nlgive only accepts online targets, so suggest online names only.
                List<String> out = onlineNames(args[0].toLowerCase(Locale.ROOT));
                for (String k : ITEM_KEYWORDS) {
                    if (k.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        out.add(k);
                    }
                }
                return out;
            }
            if (args.length == 2) {
                if (itemKeyword(args[0]) != null) {
                    return prefixed(Arrays.asList("1", "8", "16", "64"), args[1]);
                }
                return prefixed(ITEM_KEYWORDS, args[1].toLowerCase(Locale.ROOT));
            }
            if (args.length == 3) {
                return prefixed(Arrays.asList("1", "8", "16", "64"), args[2]);
            }
            return Collections.emptyList();
        }

        // setlives / revive / eliminate
        if (args.length == 1) {
            return onlineAndTrackedNames(args[0].toLowerCase(Locale.ROOT));
        }
        if (name.equals("setlives") && args.length == 2) {
            List<String> options = new ArrayList<>();
            for (int i = 0; i <= plugin.config().maxLives(); i++) {
                options.add(String.valueOf(i));
            }
            return prefixed(options, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> prefixed(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(option);
            }
        }
        return out;
    }
}
