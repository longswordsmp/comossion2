package com.longswordsmp.nolife.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** A click handler for a single slot in a {@link Menu}. */
@FunctionalInterface
public interface MenuClick {
    void run(Player viewer, InventoryClickEvent event);
}
