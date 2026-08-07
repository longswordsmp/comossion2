package com.longswordsmp.nolife.gui;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

/**
 * A tiny chest-GUI framework. A {@code Menu} owns its inventory (so it can be
 * recognised in {@link GuiListener}) and maps slots to click handlers. All
 * clicks in a Menu are cancelled by the listener, so nothing can be taken out.
 */
public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, MenuClick> actions = new HashMap<>();

    public Menu(Component title, int rows) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    /** Place a purely decorative item. */
    public void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /** Place a clickable item. */
    public void button(int slot, ItemStack item, MenuClick action) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }
    }

    /** Fill every empty slot with the given item. */
    public void fill(ItemStack item) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }

    void handle(Player viewer, int slot, InventoryClickEvent event) {
        MenuClick action = actions.get(slot);
        if (action != null) {
            action.run(viewer, event);
        }
    }

    public void open(Player viewer) {
        viewer.openInventory(inventory);
    }
}
