package com.longswordsmp.nolife.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Single listener for every {@link Menu}: cancels all clicks/drags so items
 * can't be moved, and dispatches top-inventory clicks to the menu's handlers.
 */
public class GuiListener implements Listener {

    @SuppressWarnings("unused")
    private final NoLifePlugin plugin;

    public GuiListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return; // clicked their own inventory
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        menu.handle(viewer, event.getRawSlot(), event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
