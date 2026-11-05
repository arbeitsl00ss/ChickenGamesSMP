package com.chickensmp.core.listeners;

import com.chickensmp.core.gui.ScoreboardManager;
import com.chickensmp.core.manager.PunishManager;
import com.chickensmp.core.manager.RankManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();

        if (PunishManager.isBanned(uuid)) {
            String reason = PunishManager.getBanReason(uuid);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, PunishManager.getBanMessage(reason));
            return;
        }

        if (PunishManager.isTimedOut(uuid)) {
            long remaining = PunishManager.getRemainingTimeoutSeconds(uuid);
            String reason = PunishManager.getTimeoutReason(uuid);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, PunishManager.getTimeoutMessage(reason, remaining));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ScoreboardManager.createScoreboard(player);
        RankManager.updateTabName(player);
    }
}