package com.comossion.witherstrike;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WitherStrikeCannonPlugin extends JavaPlugin {

    private CannonConfig cannonConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cannonConfig = new CannonConfig(this);

        CannonItem item = new CannonItem(this, cannonConfig);
        StrikeService strikes = new StrikeService(this, cannonConfig);

        getServer().getPluginManager().registerEvents(
                new CannonListener(this, cannonConfig, item, strikes), this);

        PluginCommand command = getCommand("witherstrikecannon");
        if (command == null) {
            getLogger().severe("Command 'witherstrikecannon' is missing from plugin.yml - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CannonCommand executor = new CannonCommand(this, cannonConfig, item);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("Wither Strike Cannon armed: " + cannonConfig.skullCount()
                + " skulls per shot, " + cannonConfig.spread() + " block spread.");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
    }

    public void reloadPlugin() {
        reloadConfig();
        cannonConfig.reload();
    }
}
