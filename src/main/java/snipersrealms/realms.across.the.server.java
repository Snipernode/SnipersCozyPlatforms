package Sniper'sRealms;

import org.bukkit.plugin.java.JavaPlugin;
import Sniper'sRealms.managers.PluginManager;
import Sniper'sRealms.listeners.PlayerListener;

public class Relams across the server extends JavaPlugin {
    
    @Override
    public void onEnable() {
        
        // Initialize managers
        PluginManager.getInstance().initialize();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        
        getLogger().info("Relams across the server has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Relams across the server has been disabled!");
    }
    
}