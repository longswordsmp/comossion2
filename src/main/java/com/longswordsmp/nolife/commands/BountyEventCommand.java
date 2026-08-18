package com.longswordsmp.nolife.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.config.PluginConfig;

/**
 * {@code /bountyevent start|stop} - an op-only announced bounty event.
 *
 * <p>Starting simply announces the event. Stopping announces it and refunds
 * every open bounty back to whoever placed it: online placers get their Life
 * Gems immediately, offline placers get them queued for their next join.</p>
 */
public class BountyEventCommand implements CommandExecutor, TabCompleter {

    private final NoLifePlugin plugin;

    public BountyEventCommand(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    private PluginConfig cfg() {
        return plugin.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nolife.bountyevent")) {
            sender.sendMessage(cfg().msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(cfg().msg("usage-bountyevent"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start", "begin", "on" -> start(sender);
            case "stop", "end", "off" -> stop(sender);
            default -> sender.sendMessage(cfg().msg("usage-bountyevent"));
        }
        return true;
    }

    private void start(CommandSender sender) {
        if (plugin.bounties().isEventActive()) {
            sender.sendMessage(cfg().msg("bountyevent-already-active"));
            return;
        }
        plugin.bounties().setEventActive(true);
        broadcast(cfg().msg("bountyevent-started"));
    }

    private void stop(CommandSender sender) {
        if (!plugin.bounties().isEventActive()) {
            sender.sendMessage(cfg().msg("bountyevent-not-active"));
            return;
        }
        plugin.bounties().setEventActive(false);

        // Refund every open bounty back to its placers.
        Map<UUID, Integer> refunds = plugin.bounties().clearAllAndComputeRefunds();
        for (Map.Entry<UUID, Integer> entry : refunds.entrySet()) {
            int amount = entry.getValue();
            if (amount <= 0) {
                continue;
            }
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) {
                plugin.items().giveLifeGems(online, amount);
                online.sendMessage(cfg().msg("bountyevent-refund", "%amount%", String.valueOf(amount)));
            } else {
                // Placer is offline: queue the gems for their next join.
                plugin.bounties().addPendingRefund(entry.getKey(), amount);
            }
        }
        broadcast(cfg().msg("bountyevent-stopped"));
    }

    private void broadcast(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nolife.bountyevent") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : Arrays.asList("start", "stop")) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
