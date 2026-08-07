package com.longswordsmp.nolife.gui;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Drives the Book of Life menus: prevents item theft, opens the confirmation
 * screen, and performs the revive when confirmed.
 */
public class GuiListener implements Listener {

    private final NoLifePlugin plugin;

    public GuiListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof ReviveMenuHolder revive) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            Player viewer = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == revive.getPrevSlot()) {
                int target = revive.getPage() - 1;
                Bukkit.getScheduler().runTask(plugin, () -> Menus.openReviveMenu(plugin, viewer, target));
                return;
            }
            if (slot == revive.getNextSlot()) {
                int target = revive.getPage() + 1;
                Bukkit.getScheduler().runTask(plugin, () -> Menus.openReviveMenu(plugin, viewer, target));
                return;
            }

            UUID target = revive.slots().get(slot);
            if (target == null) {
                return;
            }
            String name = nameOf(target);
            Bukkit.getScheduler().runTask(plugin,
                    () -> Menus.openConfirmMenu(plugin, viewer, target, name));
            return;
        }

        if (holder instanceof ConfirmMenuHolder confirm) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            Player viewer = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot == Menus.CONFIRM_SLOT) {
                handleConfirm(viewer, confirm);
            } else if (slot == Menus.CANCEL_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> Menus.openReviveMenu(plugin, viewer));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ReviveMenuHolder || holder instanceof ConfirmMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleConfirm(Player viewer, ConfirmMenuHolder confirm) {
        UUID target = confirm.getTarget();

        if (!plugin.lives().isEliminated(target)) {
            viewer.sendMessage(plugin.config().msg("already-revived"));
            close(viewer);
            return;
        }
        if (!plugin.items().consumeOneBookOfLife(viewer)) {
            viewer.sendMessage(plugin.config().msg("revive-need-book"));
            close(viewer);
            return;
        }

        plugin.lives().revive(target, viewer.getLocation(), viewer.getName());
        close(viewer);
    }

    private void close(Player viewer) {
        Bukkit.getScheduler().runTask(plugin, viewer::closeInventory);
    }

    private String nameOf(UUID uuid) {
        var data = plugin.data().get(uuid);
        if (data != null && data.getName() != null) {
            return data.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
}
