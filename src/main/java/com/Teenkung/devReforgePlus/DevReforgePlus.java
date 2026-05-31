package com.Teenkung.devReforgePlus;

import com.Teenkung.devReforgePlus.Command.DevReforgeCommandExecutor;
import com.Teenkung.devReforgePlus.Config.ConfigLoader;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeGUI;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeGUIListener;
import com.Teenkung.devReforgePlus.GUI.ReforgeGUI.ReforgeGUIManager;
import com.Teenkung.devReforgePlus.Listener.GemstoneCompatListener;
import com.Teenkung.devReforgePlus.Listener.ReforgeUpdateListener;
import com.Teenkung.devReforgePlus.Stats.TrackerStat;
import com.Teenkung.devReforgePlus.Utils.ReforgeLogger;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class DevReforgePlus extends JavaPlugin {

    // Fixed UUID that identifies OUR reforge modifier slot across all stats.
    // Using a name-based UUID means it's always the same value — no need to store it.
    public static final UUID REFORGE_UUID = UUID.nameUUIDFromBytes("dev_reforge".getBytes());
    @Getter
    private static DevReforgePlus instance;
    @Getter
    private TrackerStat trackerStat;
    @Getter
    private ConfigLoader configLoader;
    @Getter
    private ReforgeGUI reforgeGUI;
    @Getter
    private DevReforgeCommandExecutor devReforgeCommandExecutor;
    @Getter
    private ReforgeLogger reforgeLogger;
    @Getter
    private static Economy econ;

    @Override
    public void onEnable() {
        instance = this;
        setupEconomy();
        configLoader = new ConfigLoader(this);
        reforgeLogger = new ReforgeLogger(this);
        trackerStat = new TrackerStat();
        reforgeGUI = new ReforgeGUI();
        reforgeGUI.loadReforgeMenu();
        devReforgeCommandExecutor = new DevReforgeCommandExecutor(this);
        getServer().getPluginManager().registerEvents(new ReforgeGUIListener(), this);
        getServer().getPluginManager().registerEvents(new GemstoneCompatListener(), this);
        getServer().getPluginManager().registerEvents(new ReforgeUpdateListener(), this);
    }

    @Override
    public void onDisable() {
        ReforgeGUIManager.closeAll();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault is not installed! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Economy service provider not found! Disabling plugin... Please find an economy plugin and install it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        econ = rsp.getProvider();
    }
}
