package com.chickensmp.core;

import com.chickensmp.core.commands.BaltopCommand;
import com.chickensmp.core.commands.CoreCommands;
import com.chickensmp.core.commands.ChickenPermsCommand;
import com.chickensmp.core.commands.ChickenACCommand;
import com.chickensmp.core.anticheat.ChickenACListener;
import com.chickensmp.core.anticheat.ChickenACManager;
import com.chickensmp.core.commands.ExtendedCommands;
import com.chickensmp.core.discord.ConsoleForwarder;
import com.chickensmp.core.gui.ScoreboardManager;
import com.chickensmp.core.listeners.CombatListener;
import com.chickensmp.core.listeners.GUIListener;
import com.chickensmp.core.listeners.ItemValueListener;
import com.chickensmp.core.listeners.PlayerJoinListener;
import com.chickensmp.core.listeners.ResourcePackListener;
import com.chickensmp.core.manager.CombatManager;
import com.chickensmp.core.manager.ChickenPermsManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.HomeManager;
import com.chickensmp.core.manager.RankManager;
import com.chickensmp.core.manager.StatsManager;
import com.chickensmp.core.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ChickenSMP extends JavaPlugin {

    private static ChickenSMP instance;
    private ConsoleForwarder consoleForwarder;
    private ChickenACManager chickenACManager;
    private ChickenACListener chickenACListener;

    @Override
    public void onEnable() {
        instance = this;

        // Konsole live in Discord (siehe config.yml)
        saveDefaultConfig();
        mergeMissingDefaultSettings();
        consoleForwarder = new ConsoleForwarder(this);
        consoleForwarder.start();

        // 1. Manager initialisieren (YML-Dateien laden/erstellen)
        EconomyManager.init(this); // legt auch den Plugin-Ordner an
        StatsManager.init(this);
        HomeManager.init(this);
        RankManager.init(this);
        ChickenPermsManager.init(this);
        chickenACManager = new ChickenACManager(this);
        TeleportManager.init(this);
        CombatManager.init(this);

        // 2. Listener registrieren
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemValueListener(), this);
        Bukkit.getPluginManager().registerEvents(new ResourcePackListener(this), this);
        chickenACListener = new ChickenACListener(this, chickenACManager);
        Bukkit.getPluginManager().registerEvents(chickenACListener, this);

        // 3. Befehle registrieren
        CoreCommands core = new CoreCommands();
        for (String name : new String[]{"sethome", "home", "delhome", "homes", "shop", "rtp", "spawn",
                "balance", "tpa", "tpaccept", "tpdeny"}) {
            register(name, core);
        }
        for (String name : new String[]{"home", "delhome"}) {
            if (getCommand(name) != null) getCommand(name).setTabCompleter(core);
        }

        ExtendedCommands extended = new ExtendedCommands();
        for (String name : new String[]{"vanish", "punish", "timeout", "meta", "ah", "order", "sell",
                "setrank", "pay", "adminpay"}) {
            register(name, extended);
        }

        register("baltop", new BaltopCommand());
        ChickenPermsCommand chickenPerms = new ChickenPermsCommand();
        register("chicken", chickenPerms);
        getCommand("chicken").setTabCompleter(chickenPerms);

        ChickenACCommand chickenACCommand = new ChickenACCommand(this, chickenACManager);
        register("chickenac", chickenACCommand);
        if (getCommand("chickenac") != null) getCommand("chickenac").setTabCompleter(chickenACCommand);

        // Scoreboard jede Sekunde aktualisieren (Geld, Ping, Spielzeit, Online)
        Bukkit.getScheduler().runTaskTimer(this, ScoreboardManager::updateAll, 20L, 20L);

        getLogger().info("ChickenSMPCore v1.4.1 mit ChickenAC-Alert-Fix wurde erfolgreich aktiviert!");
    }

    public void reloadSettings() {
        if (consoleForwarder != null) consoleForwarder.stop();
        reloadConfig();
        mergeMissingDefaultSettings();
        consoleForwarder = new ConsoleForwarder(this);
        consoleForwarder.start();
        getLogger().info("ChickenGames-Konfiguration wurde neu geladen.");
    }


    /**
     * Adds newly introduced config.yml options to existing server configs without
     * overwriting values the server owner has already customized.
     */
    private void mergeMissingDefaultSettings() {
        try (InputStream defaultsStream = getResource("config.yml")) {
            if (defaultsStream == null) {
                getLogger().warning("Die Standard-config.yml fehlt in der Plugin-JAR.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8));
            getConfig().setDefaults(defaults);
            getConfig().options().copyDefaults(true);
            saveConfig();
        } catch (IOException exception) {
            getLogger().severe("Standard-Einstellungen konnten nicht ergänzt werden: " + exception.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (consoleForwarder != null) consoleForwarder.stop();
        if (chickenACListener != null) chickenACListener.shutdown();
        if (chickenACManager != null) chickenACManager.save();

        // Daten in die YML-Dateien speichern, wenn der Server stoppt oder reloaded wird
        EconomyManager.save();
        StatsManager.save();
        HomeManager.save();
        RankManager.save();
        ChickenPermsManager.save();

        getLogger().info("ChickenSMPCore wurde beendet. Alle Daten gespeichert!");
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Befehl /" + name + " fehlt in der plugin.yml und wurde nicht registriert!");
            return;
        }
        command.setExecutor(executor);
    }

    public static ChickenSMP getInstance() {
        return instance;
    }
}
