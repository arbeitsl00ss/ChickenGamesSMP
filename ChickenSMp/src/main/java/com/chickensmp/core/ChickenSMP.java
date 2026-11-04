package com.chickensmp.core;

import com.chickensmp.core.gui.ScoreboardManager;
import com.chickensmp.core.listeners.CombatListener;
import com.chickensmp.core.listeners.GUIListener;
import com.chickensmp.core.listeners.ItemValueListener;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChickenSMP extends JavaPlugin implements Listener {

    private static ChickenSMP instance;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Manager initialisieren (YML-Dateien laden/erstellen)
        EconomyManager.init(this);
        StatsManager.init(this);

        // Falls du einen HomeManager hast, hier einkommentieren:
        // HomeManager.init(this);

        // 2. Listener registrieren
        Bukkit.getPluginManager().registerEvents(new CombatListener(), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemValueListener(), this);

        // Registriert die Events aus dieser Main-Klasse (z.B. das Join-Event unten)
        Bukkit.getPluginManager().registerEvents(this, this);

        // 3. Commands registrieren (Hier musst du evtl. deine Command-Klassen anpassen)
        // getCommand("shop").setExecutor(new DeineCommandKlasse());
        // getCommand("ah").setExecutor(new DeineCommandKlasse());
        // getCommand("orders").setExecutor(new DeineCommandKlasse());
        // getCommand("meta").setExecutor(new DeineCommandKlasse());

        getLogger().info("ChickenSMPCore v1.2.0 wurde erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        // Daten in die YML-Dateien speichern, wenn der Server stoppt oder reloaded wird
        EconomyManager.save();
        StatsManager.save();

        // Falls du einen HomeManager hast:
        // HomeManager.save();

        getLogger().info("ChickenSMPCore wurde beendet. Alle Daten gespeichert!");
    }

    public static ChickenSMP getInstance() {
        return instance;
    }

    // Sorgt dafür, dass Spieler beim Betreten des Servers direkt ihr Scoreboard (mit Kills/Toden) bekommen
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ScoreboardManager.createScoreboard(player);
    }
}