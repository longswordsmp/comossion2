package com.longswordsmp.nolife.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.longswordsmp.nolife.NoLifePlugin;

/**
 * Dispatches inventory events for both read-only {@link Menu}s (all clicks
 * cancelled) and the editable {@link RecipeEditorHolder} (grid slots accept
 * items, buttons are cancelled, placed items returned on close).
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

        if (holder instanceof Menu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            if (event.getWhoClicked() instanceof Player viewer) {
                menu.handle(viewer, event.getRawSlot(), event);
            }
            return;
        }

        if (holder instanceof RecipeEditorHolder ed) {
            if (!(event.getWhoClicked() instanceof Player viewer)) {
                return;
            }
            if (event.getClickedInventory() == top) {
                int raw = event.getRawSlot();
                if (ed.isEditable(raw)) {
                    return; // allow placing / taking items in the 3x3 grid
                }
                event.setCancelled(true);
                Guis.handleEditorButton(plugin, viewer, ed, raw);
            }
            // Clicks in the player's own inventory are allowed. The only empty
            // slots in the top are the grid, so shift-click lands in the grid.
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof Menu) {
            event.setCancelled(true);
            return;
        }
        if (holder instanceof RecipeEditorHolder ed) {
            int topSize = top.getSize();
            for (int raw : event.getRawSlots()) {
                if (raw < topSize && !ed.isEditable(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof RecipeEditorHolder ed && event.getPlayer() instanceof Player p) {
            Guis.returnEditorItems(plugin, p, ed);
        }
    }
}
