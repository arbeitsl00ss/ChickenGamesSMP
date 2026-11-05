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

public class EconomyManager {

    private static final Map<UUID, Double> balances = new HashMap<>();
    private static File file;
    private static YamlConfiguration config;

    public static void init(JavaPlugin plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), config.getDouble(key));
            } catch (Exception ignored) {}
        }
    }

    public static void save() {
        if (file == null || config == null) return;
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static double getBalance(Player player) {
        return balances.getOrDefault(player.getUniqueId(), 1000.0);
    }

    public static void setBalance(Player player, double amount) {
        balances.put(player.getUniqueId(), Math.max(0.0, amount));
        ScoreboardManager.createScoreboard(player);
        save();
    }

    public static void addMoney(Player player, double amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public static boolean removeMoney(Player player, double amount) {
        double current = getBalance(player);
        if (current < amount) return false;
        setBalance(player, current - amount);
        return true;
    }
}