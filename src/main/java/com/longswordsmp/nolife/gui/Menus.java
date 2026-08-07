package com.longswordsmp.nolife.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.data.PlayerData;
import com.longswordsmp.nolife.util.Text;

/**
 * Builds and opens the Book of Life menus: the list of eliminated players and
 * the per-player confirmation screen.
 */
public final class Menus {

    public static final int CONFIRM_SLOT = 11;
    public static final int CANCEL_SLOT = 15;
    public static final int HEAD_SLOT = 13;

    private static final int PER_PAGE = 45; // 5 rows of heads, 6th row for nav

    private Menus() {
    }

    /** Open the first page of the eliminated-players list for {@code viewer}. */
    public static void openReviveMenu(NoLifePlugin plugin, Player viewer) {
        openReviveMenu(plugin, viewer, 0);
    }

    /** Open the given page of the eliminated-players list for {@code viewer}. */
    public static void openReviveMenu(NoLifePlugin plugin, Player viewer, int page) {
        List<PlayerData> eliminated = plugin.lives().getEliminatedSorted();
        if (eliminated.isEmpty()) {
            viewer.sendMessage(plugin.config().msg("no-eliminated"));
            return;
        }

        int totalPages = (eliminated.size() + PER_PAGE - 1) / PER_PAGE;
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        boolean paged = totalPages > 1;
        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, eliminated.size());
        int count = end - start;

        int size;
        if (paged) {
            size = 54; // 45 heads + a navigation row
        } else {
            int rows = Math.min(5, Math.max(1, (count + 8) / 9));
            size = rows * 9;
        }

        ReviveMenuHolder holder = new ReviveMenuHolder();
        holder.setPage(page);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.config().reviveTitle());
        holder.setInventory(inv);

        for (int i = 0; i < count; i++) {
            PlayerData data = eliminated.get(start + i);
            inv.setItem(i, playerHead(data.getUuid(), data.getName(),
                    "&e" + data.getName(),
                    List.of("&7Eliminated", "&aClick to select")));
            holder.slots().put(i, data.getUuid());
        }

        if (paged) {
            ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int s = size - 9; s < size; s++) {
                inv.setItem(s, filler);
            }
            inv.setItem(size - 5, pane(Material.GRAY_STAINED_GLASS_PANE,
                    "&7Page &f" + (page + 1) + "&7/&f" + totalPages));
            if (page > 0) {
                inv.setItem(size - 9, pane(Material.LIME_STAINED_GLASS_PANE, "&a◀ Previous"));
                holder.setPrevSlot(size - 9);
            }
            if (page < totalPages - 1) {
                inv.setItem(size - 1, pane(Material.LIME_STAINED_GLASS_PANE, "&aNext ▶"));
                holder.setNextSlot(size - 1);
            }
        }

        viewer.openInventory(inv);
    }

    /** Open the confirmation screen for reviving {@code target}. */
    public static void openConfirmMenu(NoLifePlugin plugin, Player viewer, UUID target, String targetName) {
        ConfirmMenuHolder holder = new ConfirmMenuHolder(target, targetName);
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.config().confirmTitle());
        holder.setInventory(inv);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(HEAD_SLOT, playerHead(target, targetName,
                "&e" + targetName, List.of("&7Bring this player back to life")));
        inv.setItem(CONFIRM_SLOT, pane(Material.LIME_STAINED_GLASS_PANE,
                "&a&lCONFIRM", List.of("&7Revive &f" + targetName)));
        inv.setItem(CANCEL_SLOT, pane(Material.RED_STAINED_GLASS_PANE,
                "&c&lCANCEL", List.of("&7Go back")));

        viewer.openInventory(inv);
    }

    // ---- item helpers -----------------------------------------------------

    private static ItemStack playerHead(UUID uuid, String name, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
            skull.setOwningPlayer(owner);
            skull.displayName(Text.item(displayName));
            skull.lore(toComponents(lore));
            head.setItemMeta(skull);
        } else if (meta != null) {
            meta.displayName(Text.item(displayName));
            meta.lore(toComponents(lore));
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack pane(Material material, String name) {
        return pane(material, name, List.of());
    }

    private static ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            if (!lore.isEmpty()) {
                meta.lore(toComponents(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<Component> toComponents(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(Text.item(line));
        }
        return out;
    }
}
