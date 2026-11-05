package com.chickensmp.core.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    private static final Map<UUID, Long> combatTimers = new HashMap<>();
    private static final Map<UUID, BukkitTask> combatTasks = new HashMap<>();
    private static JavaPlugin plugin;

    public static void init(JavaPlugin instance) {
        plugin = instance;
    }

    public static void tagPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Laufende Teleporte abbrechen
        TeleportManager.cancelTeleport(player, "Teleport abgebrochen! Du wurdest in den Kampf verwickelt.");

        combatTimers.put(uuid, System.currentTimeMillis() + (30 * 1000L));

        if (combatTasks.containsKey(uuid)) {
            combatTasks.get(uuid).cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = getRemainingSeconds(player);
                if (remaining <= 0) {
                    combatTimers.remove(uuid);
                    combatTasks.remove(uuid);
                    player.sendActionBar(Component.text("Du bist nicht mehr im Kampf!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    cancel();
                    return;
                }

                player.sendActionBar(Component.text("⚔ COMBAT (" + remaining + "s) ⚔ - Nicht ausloggen!", NamedTextColor.RED, TextDecoration.BOLD));
            }
        }.runTaskTimer(plugin, 0L, 20L);

        combatTasks.put(uuid, task);
    }

    public static boolean isInCombat(Player player) {
        if (!combatTimers.containsKey(player.getUniqueId())) return false;
        if (System.currentTimeMillis() > combatTimers.get(player.getUniqueId())) {
            combatTimers.remove(player.getUniqueId());
            if (combatTasks.containsKey(player.getUniqueId())) {
                combatTasks.get(player.getUniqueId()).cancel();
                combatTasks.remove(player.getUniqueId());
            }
            return false;
        }
        return true;
    }

    public static long getRemainingSeconds(Player player) {
        if (!combatTimers.containsKey(player.getUniqueId())) return 0;
        long diff = combatTimers.get(player.getUniqueId()) - System.currentTimeMillis();
        return Math.max(0, diff / 1000);
    }

    public static void clearCombat(Player player) {
        UUID uuid = player.getUniqueId();
        combatTimers.remove(uuid);
        if (combatTasks.containsKey(uuid)) {
            combatTasks.get(uuid).cancel();
            combatTasks.remove(uuid);
        }
    }
}