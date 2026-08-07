package com.longswordsmp.nolife.items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.config.RecipeConfig;
import com.longswordsmp.nolife.util.Text;

/**
 * Creates the Book of Life and Life Gem, tags them with persistent data so we
 * can recognise them later, and (re)registers their crafting recipes.
 */
public class ItemManager {

    private static final String TYPE_BOOK = "book_of_life";
    private static final String TYPE_GEM = "life_gem";

    private final JavaPlugin plugin;
    private final PluginConfig cfg;

    private final NamespacedKey typeKey;
    private final NamespacedKey bookRecipeKey;
    private final NamespacedKey gemRecipeKey;

    public ItemManager(JavaPlugin plugin, PluginConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.typeKey = new NamespacedKey(plugin, "type");
        this.bookRecipeKey = new NamespacedKey(plugin, "book_of_life");
        this.gemRecipeKey = new NamespacedKey(plugin, "life_gem");
    }

    // ---- creation ---------------------------------------------------------

    public ItemStack createBookOfLife(int amount) {
        ItemStack item = build(cfg.bookMaterial(), Material.BOOK, cfg.bookName(),
                cfg.bookLore(), cfg.bookModelData(), TYPE_BOOK);
        item.setAmount(amount);
        return item;
    }

    public ItemStack createLifeGem(int amount) {
        ItemStack item = build(cfg.gemMaterial(), Material.HEART_OF_THE_SEA, cfg.gemName(),
                cfg.gemLore(), cfg.gemModelData(), TYPE_GEM);
        item.setAmount(amount);
        return item;
    }

    private ItemStack build(String materialName, Material fallback, String name,
                            List<String> lore, int modelData, String type) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Unknown item material '" + materialName + "', using " + fallback);
            material = fallback;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Some materials (e.g. AIR) have no ItemMeta; fall back so we never NPE.
            plugin.getLogger().warning("Material '" + material + "' has no item meta, using " + fallback);
            item = new ItemStack(fallback);
            meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }
        }

        meta.displayName(Text.item(name));

        List<Component> loreLines = new ArrayList<>();
        String maxLives = String.valueOf(cfg.maxLives());
        for (String line : lore) {
            loreLines.add(Text.item(line.replace("%max%", maxLives)));
        }
        meta.lore(loreLines);

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.setEnchantmentGlintOverride(true);
        if (modelData > 0) {
            meta.setCustomModelData(modelData);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ---- identification ---------------------------------------------------

    public boolean isBookOfLife(ItemStack item) {
        return isType(item, TYPE_BOOK);
    }

    public boolean isLifeGem(ItemStack item) {
        return isType(item, TYPE_GEM);
    }

    private boolean isType(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String stored = meta.getPersistentDataContainer()
                .getOrDefault(typeKey, PersistentDataType.STRING, "");
        return type.equals(stored);
    }

    // ---- recipes ----------------------------------------------------------

    public void registerRecipes() {
        registerShaped(bookRecipeKey, createBookOfLife(1), cfg.bookRecipe());
        registerShaped(gemRecipeKey, createLifeGem(1), cfg.gemRecipe());
    }

    /** Remove old recipes then register fresh ones (used on /nlreload too). */
    public void reloadRecipes() {
        registerRecipes();
    }

    private void registerShaped(NamespacedKey key, ItemStack result, RecipeConfig def) {
        // Always remove any previous version first so reloads are idempotent.
        try {
            Bukkit.removeRecipe(key);
        } catch (Throwable ignored) {
            // older APIs may not have removeRecipe; safe to ignore
        }

        if (def == null || !def.enabled() || def.shape() == null || def.shape().isEmpty()) {
            return;
        }

        List<String> shape = def.shape();
        if (shape.size() > 3) {
            shape = shape.subList(0, 3);
        }

        Set<Character> shapeChars = new HashSet<>();
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ') {
                    shapeChars.add(c);
                }
            }
        }

        // Everything that can throw on a malformed recipes.yml (uneven/oversized
        // shape rows, a null ingredient material, a symbol with no ingredient)
        // is wrapped here so one bad recipe only skips itself instead of aborting
        // the whole registration / reload.
        try {
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(shape.toArray(new String[0]));

            for (Map.Entry<Character, String> entry : def.ingredients().entrySet()) {
                char symbol = entry.getKey();
                if (!shapeChars.contains(symbol)) {
                    continue; // ingredient not used by this shape
                }
                String materialName = entry.getValue();
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("Unknown material '" + materialName
                            + "' for symbol '" + symbol + "' in recipe " + key.getKey());
                    continue;
                }
                recipe.setIngredient(symbol, material);
            }

            Bukkit.addRecipe(recipe);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not register recipe " + key.getKey()
                    + " (check the shape rows are 1-3 wide and every symbol has an ingredient): "
                    + ex.getMessage());
        }
    }

    // ---- consumption ------------------------------------------------------

    /** Remove one Book of Life from a player's inventory. Returns true if found. */
    public boolean consumeOneBookOfLife(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isBookOfLife(item)) {
                consumeOne(inv, i, item);
                return true;
            }
        }
        return false;
    }

    private void consumeOne(PlayerInventory inv, int slot, ItemStack item) {
        int amount = item.getAmount();
        if (amount <= 1) {
            inv.setItem(slot, null);
        } else {
            item.setAmount(amount - 1);
            inv.setItem(slot, item);
        }
    }
}
