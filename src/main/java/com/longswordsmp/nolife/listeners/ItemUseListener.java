package com.longswordsmp.nolife.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.gui.Menus;

/**
 * Right-click behaviour for the custom items:
 * Book of Life opens the revive menu, Life Gem grants a life.
 */
public class ItemUseListener implements Listener {

    private final NoLifePlugin plugin;

    public ItemUseListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only handle the main hand so the event doesn't fire twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        boolean isBook = plugin.items().isBookOfLife(hand);
        boolean isGem = plugin.items().isLifeGem(hand);
        if (!isBook && !isGem) {
            return;
        }
        event.setCancelled(true);

        // An eliminated player (e.g. within the kick-delay window) must not be
        // able to use a Life Gem to self-revive or a Book to revive others.
        if (plugin.lives().isEliminated(player.getUniqueId())) {
            return;
        }

        if (isBook) {
            if (!player.hasPermission("nolife.use.book")) {
                player.sendMessage(plugin.config().msg("no-permission"));
                return;
            }
            Menus.openReviveMenu(plugin, player);
            return;
        }

        // isGem
        if (!player.hasPermission("nolife.use.lifegem")) {
            player.sendMessage(plugin.config().msg("no-permission"));
            return;
        }
        useLifeGem(player, hand);
    }

    private void useLifeGem(Player player, ItemStack hand) {
        UUID uuid = player.getUniqueId();
        int max = plugin.config().maxLives();
        int lives = plugin.lives().getLivesOrDefault(uuid);

        if (lives >= max) {
            player.sendMessage(plugin.config().msg("lifegem-max", "%max%", String.valueOf(max)));
            return; // do not consume the gem
        }

        int newLives = plugin.lives().setLives(uuid, lives + 1, player.getName());

        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(hand);
        }

        player.getWorld().playSound(player.getLocation(), "minecraft:entity.player.levelup", 1.0f, 1.0f);
        player.sendMessage(plugin.config().msg("lifegem-used", "%lives%", String.valueOf(newLives)));
    }
}
