package com.chickensmp.core;

import com.chickensmp.core.commands.BaltopCommand;
import com.chickensmp.core.commands.CoreCommands;
import com.chickensmp.core.commands.ExtendedCommands;
import com.chickensmp.core.discord.ConsoleForwarder;
import com.chickensmp.core.listeners.CombatListener;
import com.chickensmp.core.listeners.GUIListener;
import com.chickensmp.core.listeners.ItemValueListener;
import com.chickensmp.core.listeners.PlayerJoinListener;
import com.chickensmp.core.listeners.ResourcePackListener;
import com.chickensmp.core.manager.CombatManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.HomeManager;
import com.chickensmp.core.manager.RankManager;
import com.chickensmp.core.manager.StatsManager;
import com.chickensmp.core.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ChickenSMP extends JavaPlugin {

    private static ChickenSMP instance;
    private ConsoleForwarder consoleForwarder;

    @Override
    public void onEnable() {
        instance = this;

        // Konsole live in Discord (siehe config.yml)
        saveDefaultConfig();
        consoleForwarder = new ConsoleForwarder(this);
        consoleForwarder.start();

        // 1. Manager initialisieren (YML-Dateien laden/erstellen)
        EconomyManager.init(this); // legt auch den Plugin-Ordner an
        StatsManager.init(this);
        HomeManager.init(this);
        RankManager.init(this);
        TeleportManager.init(this);
        CombatManager.init(this);

        // 2. Listener registrieren
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemValueListener(), this);
        Bukkit.getPluginManager().registerEvents(new ResourcePackListener(this), this);

        // 3. Befehle registrieren
        CoreCommands core = new CoreCommands();
        for (String name : new String[]{"sethome", "home", "delhome", "homes", "shop", "rtp", "spawn",
                "balance", "tpa", "tpaccept", "tpdeny"}) {
            register(name, core);
        }

        ExtendedCommands extended = new ExtendedCommands();
        for (String name : new String[]{"vanish", "punish", "timeout", "meta", "ah", "order", "sell",
                "setrank", "pay", "adminpay"}) {
            register(name, extended);
        }

        register("baltop", new BaltopCommand());

        getLogger().info("ChickenSMPCore v1.2.0 wurde erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        if (consoleForwarder != null) consoleForwarder.stop();

        // Daten in die YML-Dateien speichern, wenn der Server stoppt oder reloaded wird
        EconomyManager.save();
        StatsManager.save();
        HomeManager.save();
        RankManager.save();

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
