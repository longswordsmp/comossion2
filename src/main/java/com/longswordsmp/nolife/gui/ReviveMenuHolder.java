package com.longswordsmp.nolife.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as the "list of eliminated players" menu and remembers
 * which slot maps to which player UUID, plus pagination state.
 */
public class ReviveMenuHolder implements InventoryHolder {

    private final Map<Integer, UUID> slotToTarget = new HashMap<>();
    private Inventory inventory;
    private int page;
    private int prevSlot = -1;
    private int nextSlot = -1;

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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPrevSlot() {
        return prevSlot;
    }

    public void setPrevSlot(int prevSlot) {
        this.prevSlot = prevSlot;
    }

    public int getNextSlot() {
        return nextSlot;
    }

    public void setNextSlot(int nextSlot) {
        this.nextSlot = nextSlot;
    }
}
