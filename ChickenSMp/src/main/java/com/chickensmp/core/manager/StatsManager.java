package com.chickensmp.core.manager;

import com.chickensmp.core.gui.ScoreboardManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private static final Map<UUID, Integer> killsMap = new HashMap<>();
    private static final Map<UUID, Integer> deathsMap = new HashMap<>();
    private static File file;
    private static YamlConfiguration config;

    public static void init(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "stats.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            killsMap.put(uuid, config.getInt(key + ".kills", 0));
            deathsMap.put(uuid, config.getInt(key + ".deaths", 0));
        }
    }

    public static void save() {
        for (UUID uuid : killsMap.keySet()) {
            config.set(uuid.toString() + ".kills", killsMap.get(uuid));
            config.set(uuid.toString() + ".deaths", deathsMap.get(uuid));
        }
        try { config.save(file); } catch (IOException ignored) {}
    }

    public static int getKills(Player player) {
        return killsMap.getOrDefault(player.getUniqueId(), 0);
    }

    public static void addKill(Player player) {
        killsMap.put(player.getUniqueId(), getKills(player) + 1);
        ScoreboardManager.createScoreboard(player);
        save();
    }

    public static int getDeaths(Player player) {
        return deathsMap.getOrDefault(player.getUniqueId(), 0);
    }

    public static void addDeath(Player player) {
        deathsMap.put(player.getUniqueId(), getDeaths(player) + 1);
        ScoreboardManager.createScoreboard(player);
        save();
    }
}