package com.longswordsmp.nolife;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.longswordsmp.nolife.bounty.BountyManager;
import com.longswordsmp.nolife.commands.AdminCommands;
import com.longswordsmp.nolife.commands.BountyCommand;
import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.data.DataStore;
import com.longswordsmp.nolife.gui.GuiListener;
import com.longswordsmp.nolife.items.ItemManager;
import com.longswordsmp.nolife.listeners.ChatListener;
import com.longswordsmp.nolife.listeners.ConnectionListener;
import com.longswordsmp.nolife.listeners.DamageListener;
import com.longswordsmp.nolife.listeners.DeathListener;
import com.longswordsmp.nolife.listeners.ItemUseListener;

/**
 * NoLife - a three-life survival plugin.
 *
 * <p>Wires together the config, data store, lives manager, items, listeners
 * and commands, and exposes them to the rest of the plugin.</p>
 */
public final class NoLifePlugin extends JavaPlugin {

    private PluginConfig config;
    private DataStore dataStore;
    private LivesManager livesManager;
    private ItemManager itemManager;
    private BountyManager bountyManager;
    private GodModeManager godModeManager;
    private ResourcePackService resourcePackService;

    @Override
    public void onEnable() {
        this.config = new PluginConfig(this);

        this.dataStore = new DataStore(this);
        this.dataStore.load();

        this.livesManager = new LivesManager(this, config, dataStore);
        this.livesManager.setupTeams();

        this.itemManager = new ItemManager(this, config);
        this.itemManager.registerRecipes();

        this.bountyManager = new BountyManager(this);
        this.bountyManager.load();

        this.godModeManager = new GodModeManager();

        this.resourcePackService = new ResourcePackService(this, config);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new ItemUseListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new DamageListener(this), this);
        pm.registerEvents(new ChatListener(this), this);

        AdminCommands admin = new AdminCommands(this);
        bind("nolife", admin);
        bind("setlives", admin);
        bind("revive", admin);
        bind("eliminate", admin);
        bind("nlgive", admin);
        bind("nlreload", admin);
        bind("lives", admin);
        bind("nlrecipes", admin);
        bind("godmode", admin);

        PluginCommand bountyCommand = getCommand("bounty");
        if (bountyCommand != null) {
            BountyCommand bounty = new BountyCommand(this);
            bountyCommand.setExecutor(bounty);
            bountyCommand.setTabCompleter(bounty);
        } else {
            getLogger().warning("Command 'bounty' is missing from plugin.yml.");
        }

        // Handle players already online (e.g. after /reload).
        livesManager.refreshOnlineDisplays();

        getLogger().info("NoLife enabled.");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.save();
        }
        if (bountyManager != null) {
            bountyManager.save();
        }
    }

    private void bind(String name, AdminCommands executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
        }
    }

    /** Re-read recipes and re-register the crafting recipes (used by the editor). */
    public void reloadRecipesAndItems() {
        config.reload();
        itemManager.reloadRecipes();
    }

    /** Reload config.yml, messages.yml, recipes.yml and the resource pack. */
    public void reloadAll() {
        config.reload();
        livesManager.applyColors();
        livesManager.refreshOnlineDisplays();
        itemManager.reloadRecipes();
        resourcePackService.resendAll();
    }

    // ---- accessors --------------------------------------------------------

    public PluginConfig config() {
        return config;
    }

    public DataStore data() {
        return dataStore;
    }

    public LivesManager lives() {
        return livesManager;
    }

    public ItemManager items() {
        return itemManager;
    }

    public BountyManager bounties() {
        return bountyManager;
    }

    public GodModeManager godMode() {
        return godModeManager;
    }

    public ResourcePackService resourcePacks() {
        return resourcePackService;
    }
}
