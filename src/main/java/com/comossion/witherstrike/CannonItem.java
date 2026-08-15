package com.comossion.witherstrike;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Creates and recognises the cannon fishing rod. */
public final class CannonItem {

    private final CannonConfig config;
    private final NamespacedKey key;

    public CannonItem(Plugin plugin, CannonConfig config) {
        this.config = config;
        this.key = new NamespacedKey(plugin, "wither_strike_cannon");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(config.itemName());
            meta.lore(config.itemLore());
            meta.setUnbreakable(config.itemUnbreakable());
            if (config.itemGlint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCannon(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** The hand holding a cannon rod, or null when the player is not holding one. */
    public EquipmentSlot heldIn(Player player) {
        if (isCannon(player.getInventory().getItemInMainHand())) {
            return EquipmentSlot.HAND;
        }
        if (isCannon(player.getInventory().getItemInOffHand())) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }
}
