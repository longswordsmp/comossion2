package com.longswordsmp.nolife.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as the "list of eliminated players" menu and remembers
 * which slot maps to which player UUID.
 */
public class ReviveMenuHolder implements InventoryHolder {

    private final Map<Integer, UUID> slotToTarget = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, UUID> slots() {
        return slotToTarget;
    }
}
