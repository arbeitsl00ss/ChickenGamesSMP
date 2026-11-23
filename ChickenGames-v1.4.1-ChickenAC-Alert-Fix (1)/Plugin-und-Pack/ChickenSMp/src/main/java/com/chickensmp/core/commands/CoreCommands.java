package com.chickensmp.core.commands;

import com.chickensmp.core.gui.GUIManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.HomeManager;
import com.chickensmp.core.manager.TPAManager;
import com.chickensmp.core.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoreCommands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur für Spieler!");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "sethome":
                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /sethome <Name>");
                    return true;
                }
                String homeName = args[0].toLowerCase();
                if (!HomeManager.canSetHome(player, homeName)) {
                    int limit = HomeManager.getHomeLimit();
                    player.sendMessage(ChatColor.RED + "Du hast bereits die maximale Anzahl an Homes (" + limit + ").");
                    return true;
                }
                HomeManager.setHome(player, homeName);
                player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' wurde erfolgreich erstellt!");
                player.sendMessage(ChatColor.GRAY + "Teleportiere dich mit " + ChatColor.YELLOW + "/home " + homeName
                        + ChatColor.GRAY + " oder öffne " + ChatColor.YELLOW + "/homes" + ChatColor.GRAY + ".");
                break;

            case "home":
                if (args.length < 1) {
                    GUIManager.openHomesGUI(player);
                    return true;
                }
                Location loc = HomeManager.getHome(player, args[0]);
                if (loc == null) {
                    player.sendMessage(ChatColor.RED + "Home '" + args[0] + "' existiert nicht!");
                    return true;
                }
                TeleportManager.startTeleportCountdown(player, loc, null);
                break;

            case "delhome":
                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /delhome <Name>");
                    return true;
                }
                if (HomeManager.delHome(player, args[0])) {
                    player.sendMessage(ChatColor.GREEN + "Home '" + args[0] + "' gelöscht.");
                } else {
                    player.sendMessage(ChatColor.RED + "Home '" + args[0] + "' wurde nicht gefunden.");
                }
                break;

            case "homes":
                GUIManager.openHomesGUI(player);
                break;

            case "shop":
                GUIManager.openShop(player);
                break;

            case "rtp":
                GUIManager.openRTP(player);
                break;

            case "spawn":
                TeleportManager.startTeleportCountdown(player, player.getWorld().getSpawnLocation(), null);
                break;

            case "balance", "bal":
                double money = EconomyManager.getBalance(player);
                String currency = com.chickensmp.core.ChickenSMP.getInstance().getConfig().getString("economy.currency-symbol", "$");
                player.sendMessage(ChatColor.GOLD + "Dein Kontostand: " + ChatColor.GREEN + currency + (int)money);
                break;

            case "tpa":
                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /tpa <Spieler>");
                    return true;
                }
                Player targetTpa = Bukkit.getPlayer(args[0]);
                if (targetTpa == null || !targetTpa.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                    return true;
                }
                TPAManager.sendTpaRequest(player, targetTpa);
                break;

            case "tpaccept":
                if (!TPAManager.acceptTpaRequest(player)) {
                    player.sendMessage(ChatColor.RED + "Du hast keine offene TPA-Anfrage.");
                }
                break;

            case "tpdeny":
                if (!TPAManager.denyTpaRequest(player)) {
                    player.sendMessage(ChatColor.RED + "Du hast keine offene TPA-Anfrage.");
                }
                break;
        }
        return true;
    }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        String name = command.getName().toLowerCase();
        if ((name.equals("home") || name.equals("delhome")) && args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String homeName : HomeManager.getHomes(player).keySet()) {
                if (homeName.toLowerCase().startsWith(prefix)) matches.add(homeName);
            }
            matches.sort(String.CASE_INSENSITIVE_ORDER);
            return matches;
        }
        return Collections.emptyList();
    }

}