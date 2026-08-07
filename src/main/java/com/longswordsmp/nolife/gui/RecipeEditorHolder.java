package com.longswordsmp.nolife.gui;

import java.util.Set;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as the (editable) recipe editor. Unlike {@link Menu}, the
 * grid slots accept item placement; {@link GuiListener} allows clicks in those
 * slots and cancels the rest.
 */
public class RecipeEditorHolder implements InventoryHolder {

    private final String recipeKey;
    private final Set<Integer> editableSlots;
    private Inventory inventory;

    public RecipeEditorHolder(String recipeKey, Set<Integer> editableSlots) {
        this.recipeKey = recipeKey;
        this.editableSlots = editableSlots;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String getRecipeKey() {
        return recipeKey;
    }

    public Set<Integer> editableSlots() {
        return editableSlots;
    }

    public boolean isEditable(int slot) {
        return editableSlots.contains(slot);
    }
}
