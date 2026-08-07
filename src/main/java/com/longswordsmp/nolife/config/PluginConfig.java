package com.longswordsmp.nolife.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.util.Text;

/**
 * Typed, reloadable view over the plugin's three config files:
 * config.yml, messages.yml and recipes.yml.
 */
public class PluginConfig {

    private final JavaPlugin plugin;

    private final File messagesFile;
    private final File recipesFile;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration recipes;

    // Bundled jar copies, used as a fallback when the on-disk file is missing a
    // key (e.g. an upgrade that adds new messages/recipes).
    private FileConfiguration messagesDefaults;
    private FileConfiguration recipesDefaults;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        // Write defaults to disk on first run.
        plugin.saveDefaultConfig();
        saveIfAbsent("messages.yml", messagesFile);
        saveIfAbsent("recipes.yml", recipesFile);
        reload();
    }

    /** Re-read all three files from disk. */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
        this.recipes = YamlConfiguration.loadConfiguration(recipesFile);
        this.messagesDefaults = loadJarDefaults("messages.yml");
        this.recipesDefaults = loadJarDefaults("recipes.yml");
    }

    private FileConfiguration loadJarDefaults(String resource) {
        InputStream in = plugin.getResource(resource);
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private void saveIfAbsent(String resource, File target) {
        if (!target.exists()) {
            plugin.saveResource(resource, false);
        }
    }

    // ---- core numbers -----------------------------------------------------

    public int maxLives() {
        return Math.max(1, config.getInt("max-lives", 3));
    }

    public int startingLives() {
        return clamp(config.getInt("starting-lives", 3));
    }

    public int reviveLives() {
        return clamp(config.getInt("revive-lives", 2));
    }

    public int kickDelayTicks() {
        return Math.max(0, config.getInt("kick-delay-ticks", 60));
    }

    /** Clamp a life count into the valid 0..max range. */
    public int clamp(int lives) {
        return Math.max(0, Math.min(maxLives(), lives));
    }

    // ---- colours ----------------------------------------------------------

    public ChatColor colorThree() {
        return color("colors.three-lives", ChatColor.GREEN);
    }

    public ChatColor colorTwo() {
        return color("colors.two-lives", ChatColor.YELLOW);
    }

    public ChatColor colorOne() {
        return color("colors.one-life", ChatColor.RED);
    }

    private ChatColor color(String path, ChatColor def) {
        String raw = config.getString(path);
        if (raw == null) {
            return def;
        }
        try {
            ChatColor c = ChatColor.valueOf(raw.toUpperCase(Locale.ROOT));
            return c.isColor() ? c : def;
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    // ---- items ------------------------------------------------------------

    public String bookMaterial() {
        return config.getString("items.book-of-life.material", "BOOK");
    }

    public String bookName() {
        return config.getString("items.book-of-life.name", "&6&lBook of Life");
    }

    public List<String> bookLore() {
        return config.getStringList("items.book-of-life.lore");
    }

    public int bookModelData() {
        return config.getInt("items.book-of-life.custom-model-data", 0);
    }

    public String gemMaterial() {
        return config.getString("items.life-gem.material", "HEART_OF_THE_SEA");
    }

    public String gemName() {
        return config.getString("items.life-gem.name", "&c&lLife Gem");
    }

    public List<String> gemLore() {
        return config.getStringList("items.life-gem.lore");
    }

    public int gemModelData() {
        return config.getInt("items.life-gem.custom-model-data", 0);
    }

    // ---- recipes (recipes.yml) -------------------------------------------

    public RecipeConfig bookRecipe() {
        return recipe("book-of-life");
    }

    public RecipeConfig gemRecipe() {
        return recipe("life-gem");
    }

    private RecipeConfig recipe(String path) {
        ConfigurationSection section = recipes.getConfigurationSection(path);
        if (section == null && recipesDefaults != null) {
            section = recipesDefaults.getConfigurationSection(path);
        }
        if (section == null) {
            return new RecipeConfig(false, new ArrayList<>(), new LinkedHashMap<>());
        }
        boolean enabled = section.getBoolean("enabled", true);
        List<String> shape = section.getStringList("shape");

        Map<Character, String> ingredients = new LinkedHashMap<>();
        ConfigurationSection ing = section.getConfigurationSection("ingredients");
        if (ing != null) {
            for (String key : ing.getKeys(false)) {
                if (!key.isEmpty()) {
                    ingredients.put(key.charAt(0), ing.getString(key));
                }
            }
        }
        return new RecipeConfig(enabled, shape, ingredients);
    }

    // ---- gui --------------------------------------------------------------

    public Component reviveTitle() {
        return Text.parse(config.getString("gui.revive-title", "&8Book of Life"));
    }

    public Component confirmTitle() {
        return Text.parse(config.getString("gui.confirm-title", "&8Revive this player?"));
    }

    // ---- resource pack ----------------------------------------------------

    public boolean resourcePackEnabled() {
        return config.getBoolean("resource-pack.enabled", false);
    }

    public String resourcePackVersion() {
        return config.getString("resource-pack.version", "26.1.2");
    }

    public String resourcePackUrl() {
        return config.getString("resource-pack.url", "");
    }

    public String resourcePackSha1() {
        return config.getString("resource-pack.sha1", "");
    }

    public String resourcePackPrompt() {
        return config.getString("resource-pack.prompt", "");
    }

    public boolean resourcePackForce() {
        return config.getBoolean("resource-pack.force", false);
    }

    public boolean resourcePackSendOnJoin() {
        return config.getBoolean("resource-pack.send-on-join", true);
    }

    // ---- messages (messages.yml) -----------------------------------------

    /** Raw (un-parsed) message string for a dotted key in messages.yml. */
    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null && messagesDefaults != null) {
            value = messagesDefaults.getString(key);
        }
        return value != null ? value : "";
    }

    /**
     * Build a message component, replacing {@code %token%} placeholders. Pairs
     * are supplied as token, value, token, value, ...
     */
    public Component msg(String key, String... replacements) {
        String s = raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            s = s.replace(replacements[i], replacements[i + 1]);
        }
        return Text.parse(s);
    }
}
