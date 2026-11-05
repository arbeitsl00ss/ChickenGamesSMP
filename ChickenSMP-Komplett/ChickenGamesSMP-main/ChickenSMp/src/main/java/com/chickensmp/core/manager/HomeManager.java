package com.chickensmp.core.manager;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private static final Map<UUID, Map<String, Location>> playerHomes = new HashMap<>();
    private static File file;
    private static YamlConfiguration config;

    public static void init(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "homes.yml");
        config = YamlConfiguration.loadConfiguration(file);

        for (String uuidStr : config.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, Location> homes = new HashMap<>();
            if (config.isConfigurationSection(uuidStr)) {
                for (String homeName : config.getConfigurationSection(uuidStr).getKeys(false)) {
                    Location loc = config.getLocation(uuidStr + "." + homeName);
                    if (loc != null) homes.put(homeName, loc);
                }
            }
            playerHomes.put(uuid, homes);
        }
    }

    public static void save() {
        for (Map.Entry<UUID, Map<String, Location>> entry : playerHomes.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Location> homeEntry : entry.getValue().entrySet()) {
                config.set(uuidStr + "." + homeEntry.getKey(), homeEntry.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        playerHomes.putIfAbsent(uuid, new HashMap<>());
        playerHomes.get(uuid).put(name.toLowerCase(), player.getLocation());
        save();
    }

    public static Location getHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (!playerHomes.containsKey(uuid)) return null;
        return playerHomes.get(uuid).get(name.toLowerCase());
    }

    public static boolean delHome(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (!playerHomes.containsKey(uuid)) return false;
        boolean removed = playerHomes.get(uuid).remove(name.toLowerCase()) != null;
        if (removed) {
            config.set(uuid.toString() + "." + name.toLowerCase(), null);
            save();
        }
        return removed;
    }

    public static Map<String, Location> getHomes(Player player) {
        return playerHomes.getOrDefault(player.getUniqueId(), new HashMap<>());
    }
}