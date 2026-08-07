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

    private Menus() {
    }

    /** Open the list of eliminated players for {@code viewer}. */
    public static void openReviveMenu(NoLifePlugin plugin, Player viewer) {
        List<PlayerData> eliminated = plugin.lives().getEliminatedSorted();
        if (eliminated.isEmpty()) {
            viewer.sendMessage(plugin.config().msg("no-eliminated"));
            return;
        }

        int rows = Math.min(6, Math.max(1, (eliminated.size() + 8) / 9));
        int size = rows * 9;

        ReviveMenuHolder holder = new ReviveMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, plugin.config().reviveTitle());
        holder.setInventory(inv);

        int shown = Math.min(eliminated.size(), size);
        for (int i = 0; i < shown; i++) {
            PlayerData data = eliminated.get(i);
            inv.setItem(i, playerHead(data.getUuid(), data.getName(),
                    "&e" + data.getName(),
                    List.of("&7Eliminated", "&aClick to select")));
            holder.slots().put(i, data.getUuid());
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
