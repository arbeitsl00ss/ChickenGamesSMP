package com.chickensmp.core.manager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TPAManager {

    private static final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private static final Map<UUID, Long> tpaTimers = new HashMap<>();

    public static void sendTpaRequest(Player sender, Player target) {
        tpaRequests.put(target.getUniqueId(), sender.getUniqueId());
        tpaTimers.put(target.getUniqueId(), System.currentTimeMillis() + (15 * 1000L));

        sender.sendMessage(ChatColor.GREEN + "TPA-Anfrage an " + target.getName() + " gesendet! (Gültig für 15s)");
        target.sendMessage(ChatColor.GOLD + sender.getName() + " möchte sich zu dir teleportieren!");
        target.sendMessage(ChatColor.YELLOW + "Nutze " + ChatColor.GREEN + "/tpaccept" + ChatColor.YELLOW + " oder " + ChatColor.RED + "/tpdeny");
    }

    public static boolean acceptTpaRequest(Player target) {
        UUID targetId = target.getUniqueId();
        if (!hasValidRequest(targetId)) return false;

        UUID senderId = tpaRequests.get(targetId);
        Player sender = target.getServer().getPlayer(senderId);

        if (sender != null && sender.isOnline()) {
            TeleportManager.startTeleportCountdown(sender, target.getLocation(), () -> {
                target.sendMessage(ChatColor.GREEN + sender.getName() + " wurde zu dir teleportiert.");
            });
        } else {
            target.sendMessage(ChatColor.RED + "Der Spieler ist nicht mehr online.");
        }

        clearRequest(targetId);
        return true;
    }

    public static boolean denyTpaRequest(Player target) {
        UUID targetId = target.getUniqueId();
        if (!hasValidRequest(targetId)) return false;

        UUID senderId = tpaRequests.get(targetId);
        Player sender = target.getServer().getPlayer(senderId);

        if (sender != null && sender.isOnline()) {
            sender.sendMessage(ChatColor.RED + target.getName() + " hat deine TPA-Anfrage abgelehnt.");
        }
        target.sendMessage(ChatColor.YELLOW + "TPA-Anfrage abgelehnt.");

        clearRequest(targetId);
        return true;
    }

    private static boolean hasValidRequest(UUID targetId) {
        if (!tpaRequests.containsKey(targetId)) return false;
        if (System.currentTimeMillis() > tpaTimers.getOrDefault(targetId, 0L)) {
            clearRequest(targetId);
            return false;
        }
        return true;
    }

    private static void clearRequest(UUID targetId) {
        tpaRequests.remove(targetId);
        tpaTimers.remove(targetId);
    }
}