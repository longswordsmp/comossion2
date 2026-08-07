package com.longswordsmp.nolife.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as the revive confirmation screen and remembers which
 * player it is about.
 */
public class ConfirmMenuHolder implements InventoryHolder {

    private final UUID target;
    private final String targetName;
    private Inventory inventory;

    public ConfirmMenuHolder(UUID target, String targetName) {
        this.target = target;
        this.targetName = targetName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTarget() {
        return target;
    }

    public String getTargetName() {
        return targetName;
    }
}
