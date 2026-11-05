package com.chickensmp.core.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Schickt das Menue-Pack beim Beitritt an den Spieler und meldet, wenn etwas schiefgeht.
 * Einstellungen stehen in der config.yml unter "resource-pack". Fehlen sie, gelten die Standardwerte unten.
 */
public class ResourcePackListener implements Listener {

    private static final String DEFAULT_URL =
            "https://raw.githubusercontent.com/arbeitsl00ss/ChickenGamesSMP/main/ChickenGames-Pack.zip";
    private static final String DEFAULT_SHA1 = "02069beb51ec8a73d8c3d7c72b8406cd5e005780";

    private final JavaPlugin plugin;

    public ResourcePackListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("resource-pack.enabled", true)) return;

        String url = cfg.getString("resource-pack.url", DEFAULT_URL).trim();
        String sha1 = cfg.getString("resource-pack.sha1", DEFAULT_SHA1).trim();
        boolean required = cfg.getBoolean("resource-pack.required", false);

        byte[] hash = parseSha1(sha1);
        if (url.isEmpty() || hash == null) {
            plugin.getLogger().warning("Resource Pack: url oder sha1 in der config.yml sind ungueltig (sha1 = 40 Zeichen 0-9/a-f).");
            return;
        }

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getLogger().info("Resource Pack: sende an " + player.getName());
            player.setResourcePack(url, hash, Component.text("Chicken Games Menü-Pack"), required);
        }, 40L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        String status = event.getStatus().name();

        switch (status) {
            case "SUCCESSFULLY_LOADED" ->
                    plugin.getLogger().info("Resource Pack: bei " + player.getName() + " geladen.");
            case "DECLINED" -> {
                plugin.getLogger().info("Resource Pack: " + player.getName() + " hat es abgelehnt.");
                player.sendMessage(ChatColor.YELLOW + "Du hast das Menü-Pack abgelehnt. Aktiviere in der Serverliste "
                        + "bei diesem Server \"Server-Ressourcenpakete: Aktiviert\", damit die Menüs gut aussehen.");
            }
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD" -> {
                plugin.getLogger().warning("Resource Pack: bei " + player.getName() + " fehlgeschlagen (" + status
                        + "). URL und sha1 in der config.yml pruefen und pruefen, ob die ZIP wirklich unter der URL liegt.");
                player.sendMessage(ChatColor.RED + "Das Menü-Pack konnte nicht geladen werden (" + status + "). "
                        + "Die Menüs sehen deshalb normal aus.");
            }
            default -> {
            }
        }
    }

    private static byte[] parseSha1(String hex) {
        if (hex.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
