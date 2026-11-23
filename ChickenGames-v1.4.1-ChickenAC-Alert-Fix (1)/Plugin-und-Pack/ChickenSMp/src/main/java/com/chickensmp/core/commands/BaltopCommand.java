package com.chickensmp.core.commands;

import com.chickensmp.core.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class BaltopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "=== " + ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "BALTOP - REICHSTE SPIELER" + ChatColor.DARK_PURPLE + " ===");

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort((p1, p2) -> Double.compare(EconomyManager.getBalance(p2), EconomyManager.getBalance(p1)));

        int rank = 1;
        for (Player p : onlinePlayers) {
            if (rank > 10) break;
            sender.sendMessage(ChatColor.GOLD + "#" + rank + " " + ChatColor.YELLOW + p.getName() +
                    ChatColor.GRAY + " - " + ChatColor.GREEN + "$" + (int)EconomyManager.getBalance(p));
            rank++;
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "=================================");
        return true;
    }
}