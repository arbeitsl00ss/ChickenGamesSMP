package com.chickensmp.core.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

public class PunishManager {

    private static final Map<UUID, String> bannedPlayers = new HashMap<>();
    private static final Map<UUID, Long> timeoutPlayers = new HashMap<>();
    private static final Map<UUID, String> timeoutReasons = new HashMap<>();
    private static final Set<UUID> vanishedPlayers = new HashSet<>();

    public static void toggleVanish(Player player) {
        if (vanishedPlayers.contains(player.getUniqueId())) {
            vanishedPlayers.remove(player.getUniqueId());
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(ChatColor.RED + "Du bist nun nicht mehr im Vanish!");
        } else {
            vanishedPlayers.add(player.getUniqueId());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.GREEN + "Du bist jetzt im Vanish (Spectator)!");
        }
    }

    public static void banPlayer(UUID uuid, String reason) {
        bannedPlayers.put(uuid, reason);
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            target.kickPlayer(getBanMessage(reason));
        }
    }

    public static boolean isBanned(UUID uuid) {
        return bannedPlayers.containsKey(uuid);
    }

    public static String getBanReason(UUID uuid) {
        return bannedPlayers.getOrDefault(uuid, "Kein Grund angegeben.");
    }

    public static String getBanMessage(String reason) {
        return ChatColor.RED + "" + ChatColor.BOLD + "Du wurdest von Chicken Games gebannt!\n\n" +
                ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + reason + "\n\n" +
                ChatColor.GRAY + "Du kannst dich im Discord für Unban melden.";
    }

    public static void timeoutPlayer(UUID uuid, int seconds, String reason) {
        long expireTime = System.currentTimeMillis() + (seconds * 1000L);
        timeoutPlayers.put(uuid, expireTime);
        timeoutReasons.put(uuid, reason);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            target.kickPlayer(getTimeoutMessage(reason, seconds));
        }
    }

    public static boolean isTimedOut(UUID uuid) {
        if (!timeoutPlayers.containsKey(uuid)) return false;
        if (System.currentTimeMillis() > timeoutPlayers.get(uuid)) {
            timeoutPlayers.remove(uuid);
            timeoutReasons.remove(uuid);
            return false;
        }
        return true;
    }

    public static String getTimeoutMessage(String reason, long remainingSeconds) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "Du hast ein Timeout auf Chicken Games!\n\n" +
                ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.YELLOW + "Verbleibende Zeit: " + ChatColor.WHITE + remainingSeconds + " Sekunden\n\n" +
                ChatColor.GRAY + "Warte, bis die Zeit abgelaufen ist.";
    }

    public static long getRemainingTimeoutSeconds(UUID uuid) {
        if (!timeoutPlayers.containsKey(uuid)) return 0;
        long diff = timeoutPlayers.get(uuid) - System.currentTimeMillis();
        return Math.max(0, diff / 1000);
    }

    public static String getTimeoutReason(UUID uuid) {
        return timeoutReasons.getOrDefault(uuid, "Kein Grund angegeben.");
    }
}