package com.comossion.witherstrike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** /witherstrikecannon [give &lt;player&gt; | reload] */
public final class CannonCommand implements CommandExecutor, TabCompleter {

    private final WitherStrikeCannonPlugin plugin;
    private final CannonConfig config;
    private final CannonItem item;

    public CannonCommand(WitherStrikeCannonPlugin plugin, CannonConfig config, CannonItem item) {
        this.plugin = plugin;
        this.config = config;
        this.item = item;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.message("player-only"));
                return true;
            }
            give(player);
            player.sendMessage(config.message("received"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("witherstrikecannon.reload")) {
                    sender.sendMessage(config.message("no-permission"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(config.message("reloaded"));
            }
            case "give" -> {
                if (!sender.hasPermission("witherstrikecannon.give")) {
                    sender.sendMessage(config.message("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(config.message("usage"));
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(config.message("player-not-found", "%player%", args[1]));
                    return true;
                }
                give(target);
                target.sendMessage(config.message("received"));
                sender.sendMessage(config.message("given", "%player%", target.getName()));
            }
            default -> sender.sendMessage(config.message("usage"));
        }
        return true;
    }

    private void give(Player player) {
        ItemStack cannon = item.create();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(cannon);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("witherstrikecannon.give")) {
                options.add("give");
            }
            if (sender.hasPermission("witherstrikecannon.reload")) {
                options.add("reload");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")
                && sender.hasPermission("witherstrikecannon.give")) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
