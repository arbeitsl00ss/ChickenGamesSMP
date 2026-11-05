package com.chickensmp.core.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private static final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private static JavaPlugin plugin;

    public static void init(JavaPlugin instance) {
        plugin = instance;
    }

    public static void startTeleportCountdown(Player player, Location targetLoc, Runnable onComplete) {
        if (CombatManager.isInCombat(player)) {
            player.sendMessage(ChatColor.RED + "Du kannst dich im Kampf nicht teleportieren! (" + CombatManager.getRemainingSeconds(player) + "s verbleibend)");
            return;
        }

        cancelTeleport(player, null);

        Location startLoc = player.getLocation().clone();

        BukkitTask task = new BukkitRunnable() {
            int secondsLeft = 3;

            @Override
            public void run() {
                if (player.getLocation().distanceSquared(startLoc) > 0.1) {
                    cancelTeleport(player, "Teleport abgebrochen! Du hast dich bewegt.");
                    cancel();
                    return;
                }

                if (CombatManager.isInCombat(player)) {
                    cancelTeleport(player, "Teleport abgebrochen! Du bist im Kampf.");
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    player.sendActionBar(Component.text("Teleportation in " + secondsLeft + "s... Stehe still!", NamedTextColor.YELLOW));
                    secondsLeft--;
                } else {
                    pendingTeleports.remove(player.getUniqueId());
                    player.teleport(targetLoc);
                    player.sendActionBar(Component.text("Erfolgreich teleportiert!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    if (onComplete != null) onComplete.run();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), task);
    }

    public static void cancelTeleport(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            pendingTeleports.get(uuid).cancel();
            pendingTeleports.remove(uuid);
            if (reason != null) {
                player.sendMessage(ChatColor.RED + reason);
                player.sendActionBar(Component.text("Teleport abgebrochen!", NamedTextColor.RED, TextDecoration.BOLD));
            }
        }
    }
}