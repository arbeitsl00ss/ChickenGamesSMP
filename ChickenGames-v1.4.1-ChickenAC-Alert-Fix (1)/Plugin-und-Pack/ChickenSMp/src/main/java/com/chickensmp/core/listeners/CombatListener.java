package com.chickensmp.core.listeners;

import com.chickensmp.core.manager.CombatManager;
import com.chickensmp.core.manager.StatsManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatListener implements Listener {

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            CombatManager.tagPlayer(victim);
            CombatManager.tagPlayer(attacker);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        StatsManager.addDeath(victim);
        CombatManager.clearCombat(victim);

        if (victim.getKiller() != null) {
            Player killer = victim.getKiller();
            StatsManager.addKill(killer);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (CombatManager.isInCombat(player)) {
            player.setHealth(0.0);
            StatsManager.addDeath(player);
            CombatManager.clearCombat(player);
            event.setQuitMessage(ChatColor.RED + player.getName() + " ist im Kampf ausgestorben (Combat Log)!");
        }
    }
}