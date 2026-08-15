package com.longswordsmp.nolife.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.bounty.Bounty;
import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.data.PlayerData;
import com.longswordsmp.nolife.gui.Guis;

/**
 * The {@code /bounty} command: stake Life Gems on a player, reclaim your own
 * stake, or view active bounties. Whoever kills a bountied player collects the
 * staked gems (paid out in {@link com.longswordsmp.nolife.listeners.DeathListener}).
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final NoLifePlugin plugin;

    public BountyCommand(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    private PluginConfig cfg() {
        return plugin.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nolife.bounty")) {
            sender.sendMessage(cfg().msg("no-permission"));
            return true;
        }
        if (!cfg().bountyEnabled()) {
            sender.sendMessage(cfg().msg("bounty-disabled"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add", "set", "place" -> add(sender, args);
            case "remove", "cancel", "take", "reclaim" -> remove(sender, args);
            default -> sender.sendMessage(cfg().msg("usage-bounty"));
        }
        return true;
    }

    // ---- /bounty add <player> <amount> -----------------------------------

    private void add(CommandSender sender, String[] args) {
        if (!(sender instanceof Player placer)) {
            sender.sendMessage(cfg().msg("players-only"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(cfg().msg("usage-bounty"));
            return;
        }
        UUID target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[1]));
            return;
        }
        String targetName = displayName(target, args[1]);

        if (!cfg().bountyAllowSelf() && target.equals(placer.getUniqueId())) {
            sender.sendMessage(cfg().msg("bounty-self"));
            return;
        }
        if (plugin.lives().isEliminated(target)) {
            sender.sendMessage(cfg().msg("bounty-target-eliminated", "%player%", targetName));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(cfg().msg("invalid-number", "%amount%", args[2]));
            return;
        }
        int min = cfg().bountyMinAmount();
        if (amount < min) {
            sender.sendMessage(cfg().msg("bounty-min-amount", "%min%", String.valueOf(min)));
            return;
        }

        int maxPer = cfg().bountyMaxPerPlayer();
        if (maxPer > 0) {
            int already = plugin.bounties().contribution(target, placer.getUniqueId());
            if (already + amount > maxPer) {
                sender.sendMessage(cfg().msg("bounty-max-per-player",
                        "%max%", String.valueOf(maxPer), "%player%", targetName));
                return;
            }
        }

        int have = plugin.items().countLifeGems(placer);
        if (have < amount) {
            sender.sendMessage(cfg().msg("bounty-need-gems",
                    "%amount%", String.valueOf(amount), "%have%", String.valueOf(have)));
            return;
        }

        // Take the gems first, then escrow exactly what was actually removed, so
        // the pot can never hold more gems than left the placer's inventory.
        int taken = plugin.items().removeLifeGems(placer, amount);
        if (taken <= 0) {
            sender.sendMessage(cfg().msg("bounty-need-gems",
                    "%amount%", String.valueOf(amount), "%have%", String.valueOf(have)));
            return;
        }
        int total = plugin.bounties().add(target, targetName,
                placer.getUniqueId(), placer.getName(), taken);

        sender.sendMessage(cfg().msg("bounty-placed",
                "%amount%", String.valueOf(taken),
                "%player%", targetName,
                "%total%", String.valueOf(total)));
        if (cfg().bountyBroadcastPlaced()) {
            broadcastExcept(placer, cfg().msg("bounty-placed-broadcast",
                    "%placer%", placer.getName(),
                    "%player%", targetName,
                    "%total%", String.valueOf(total)));
        }
    }

    // ---- /bounty remove <player> -----------------------------------------

    private void remove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player placer)) {
            sender.sendMessage(cfg().msg("players-only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(cfg().msg("usage-bounty"));
            return;
        }
        UUID target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(cfg().msg("unknown-player", "%player%", args[1]));
            return;
        }
        String targetName = displayName(target, args[1]);
        if (plugin.bounties().get(target) == null) {
            sender.sendMessage(cfg().msg("bounty-none", "%player%", targetName));
            return;
        }
        int refunded = plugin.bounties().removeContribution(target, placer.getUniqueId());
        if (refunded <= 0) {
            sender.sendMessage(cfg().msg("bounty-no-contribution", "%player%", targetName));
            return;
        }
        plugin.items().giveLifeGems(placer, refunded);
        sender.sendMessage(cfg().msg("bounty-removed",
                "%amount%", String.valueOf(refunded), "%player%", targetName));
    }

    // ---- /bounty list ----------------------------------------------------

    private void list(CommandSender sender) {
        if (sender instanceof Player player) {
            Guis.openBounties(plugin, player, 0);
            return;
        }
        if (plugin.bounties().isEmpty()) {
            sender.sendMessage(cfg().msg("bounty-none-active"));
            return;
        }
        sender.sendMessage(cfg().msg("bounty-list-header"));
        for (Bounty b : plugin.bounties().allSorted()) {
            sender.sendMessage(cfg().msg("bounty-list-line",
                    "%player%", b.targetName(), "%amount%", String.valueOf(b.total())));
        }
    }

    // ---- helpers ----------------------------------------------------------

    private void broadcastExcept(Player except, Component component) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(except.getUniqueId())) {
                p.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

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

    // ---- tab completion --------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nolife.bounty") || !cfg().bountyEnabled()) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return prefixed(Arrays.asList("add", "remove", "list"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("add") || sub.equals("set") || sub.equals("place")) {
                return targetableNames(args[1], sender);
            }
            if (sub.equals("remove") || sub.equals("cancel") || sub.equals("take") || sub.equals("reclaim")) {
                return bountyTargetNames(args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 3 && (sub.equals("add") || sub.equals("set") || sub.equals("place"))) {
            List<String> options = new ArrayList<>();
            if (sender instanceof Player p) {
                int have = plugin.items().countLifeGems(p);
                if (have > 0) {
                    options.add(String.valueOf(have));
                }
            }
            for (String n : new String[]{"1", "5", "10", "32", "64"}) {
                if (!options.contains(n)) {
                    options.add(n);
                }
            }
            return prefixed(options, args[2]);
        }
        return Collections.emptyList();
    }

    /** Names you can place a bounty on: online + tracked, alive, minus self. */
    private List<String> targetableNames(String prefix, CommandSender sender) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        for (PlayerData d : plugin.data().all()) {
            if (d.getName() != null && !d.isEliminated()) {
                names.add(d.getName());
            }
        }
        if (!cfg().bountyAllowSelf() && sender instanceof Player p) {
            names.remove(p.getName());
        }
        return matching(names, prefix);
    }

    /** Names that currently have a bounty (for /bounty remove). */
    private List<String> bountyTargetNames(String prefix) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Bounty b : plugin.bounties().allSorted()) {
            if (b.targetName() != null) {
                names.add(b.targetName());
            }
        }
        return matching(names, prefix);
    }

    private List<String> matching(TreeSet<String> names, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String candidate : names) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private List<String> prefixed(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
