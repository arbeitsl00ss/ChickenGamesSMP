package com.chickensmp.core.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankManager {

    private static final Map<UUID, String> ranks = new HashMap<>();
    private static File file;
    private static YamlConfiguration config;

    public static void init(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "ranks.yml");
        config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            ranks.put(UUID.fromString(key), config.getString(key));
        }
    }

    public static void save() {
        for (Map.Entry<UUID, String> entry : ranks.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getRank(Player player) {
        return ranks.getOrDefault(player.getUniqueId(), "Spieler");
    }

    public static void setRank(Player player, String rank) {
        ranks.put(player.getUniqueId(), rank);
        updateTabName(player);
        save();
    }

    public static boolean hasStaffPermission(Player player) {
        String rank = getRank(player).toLowerCase();
        return rank.equals("owner") || rank.equals("developer") || rank.equals("mod") || rank.equals("admin") || player.isOp();
    }

    public static void updateTabName(Player player) {
        String rank = getRank(player);
        ChatColor color = switch (rank.toLowerCase()) {
            case "owner", "admin" -> ChatColor.RED;
            case "developer" -> ChatColor.AQUA;
            case "mod" -> ChatColor.DARK_GREEN;
            case "vip" -> ChatColor.GOLD;
            default -> ChatColor.GRAY;
        };

        String prefix = color + "[" + rank + "] " + ChatColor.WHITE;
        player.setPlayerListName(prefix + player.getName());
    }
}