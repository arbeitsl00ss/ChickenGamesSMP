package com.chickensmp.core.gui;

import com.chickensmp.core.manager.CombatManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class ScoreboardManager {

    public static void createScoreboard(Player player) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        Objective obj = board.registerNewObjective("chickensmp", Criteria.DUMMY,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "CHICKEN" + ChatColor.RED + ChatColor.BOLD + "SMP");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        double balance = EconomyManager.getBalance(player);
        String formattedBalance = formatMoney(balance);
        int kills = StatsManager.getKills(player);
        int deaths = StatsManager.getDeaths(player);

        obj.getScore(ChatColor.DARK_PURPLE + "-------------------").setScore(10);
        obj.getScore(ChatColor.GREEN + "$ " + ChatColor.WHITE + "Geld " + ChatColor.GREEN + formattedBalance).setScore(9);
        obj.getScore(ChatColor.WHITE + "⚔ " + ChatColor.WHITE + "Kills " + ChatColor.RED + kills).setScore(8);
        obj.getScore(ChatColor.DARK_RED + "☠ " + ChatColor.WHITE + "Tode " + ChatColor.RED + deaths).setScore(7);
        obj.getScore(ChatColor.GOLD + "🛡 " + ChatColor.WHITE + "Clan " + ChatColor.GOLD + "???").setScore(6);
        obj.getScore(ChatColor.AQUA + "🔑 " + ChatColor.WHITE + "Keyall " + ChatColor.GREEN + "26m 54s").setScore(5);
        obj.getScore(ChatColor.GREEN + "⏱ " + ChatColor.WHITE + "Spieler " + ChatColor.AQUA + Bukkit.getOnlinePlayers().size()).setScore(4);
        obj.getScore(ChatColor.DARK_PURPLE + "------------------- ").setScore(3);
        obj.getScore(ChatColor.RED + "CHICKENSMP.NET").setScore(2);

        player.setScoreboard(board);
    }

    private static String formatMoney(double money) {
        if (money >= 1_000_000_000) {
            return String.format("%.2fB", money / 1_000_000_000);
        } else if (money >= 1_000_000) {
            return String.format("%.2fM", money / 1_000_000);
        } else if (money >= 1_000) {
            return String.format("%.2fk", money / 1_000);
        }
        return String.format("%.0f", money);
    }
}