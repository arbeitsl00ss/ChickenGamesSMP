package com.chickensmp.core.commands;

import com.chickensmp.core.gui.GUIManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.MetaManager;
import com.chickensmp.core.manager.PunishManager;
import com.chickensmp.core.manager.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ExtendedCommands implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler!");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "vanish":
                if (!RankManager.hasStaffPermission(player)) {
                    player.sendMessage(ChatColor.RED + "Dazu benötigst du den Rang Mod, Developer oder Owner!");
                    return true;
                }
                PunishManager.toggleVanish(player);
                break;

            case "punish":
                if (!RankManager.hasStaffPermission(player)) {
                    player.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /punish <Spieler> <Grund>");
                    return true;
                }
                Player targetBan = Bukkit.getPlayer(args[0]);
                String banReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                if (targetBan != null) {
                    PunishManager.banPlayer(targetBan.getUniqueId(), banReason);
                    player.sendMessage(ChatColor.GREEN + targetBan.getName() + " wurde gebannt!");
                } else {
                    player.sendMessage(ChatColor.RED + "Spieler ist nicht online!");
                }
                break;

            case "timeout":
                if (!RankManager.hasStaffPermission(player)) {
                    player.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /timeout <Spieler> <Sekunden> <Grund>");
                    return true;
                }
                Player targetTimeout = Bukkit.getPlayer(args[0]);
                if (targetTimeout == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                    return true;
                }

                try {
                    int seconds = Integer.parseInt(args[1]);
                    String timeoutReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    PunishManager.timeoutPlayer(targetTimeout.getUniqueId(), seconds, timeoutReason);
                    player.sendMessage(ChatColor.GREEN + targetTimeout.getName() + " hat ein Timeout für " + seconds + "s erhalten.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Ungültige Sekunden-Anzahl!");
                }
                break;

            case "meta", "setmeta":
                if (!RankManager.hasStaffPermission(player)) {
                    player.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte!");
                    return true;
                }

                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.RED + "Du musst ein Item in der Hand halten!");
                    return true;
                }

                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /meta <Preis>");
                    return true;
                }

                try {
                    double newPrice = Double.parseDouble(args[0]);
                    MetaManager.setCustomPrice(itemInHand, newPrice);
                    player.sendMessage(ChatColor.GREEN + "Der Verkaufspreis für dieses Item wurde auf $" + (int)newPrice + " gesetzt!");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Ungültiger Preis! Bitte gib eine Zahl ein.");
                }
                break;

            case "ah":
                GUIManager.openAH(player);
                break;

            case "order":
                GUIManager.openOrders(player);
                break;
            case "sell":
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.RED + "Du musst ein Item in der Hand halten!");
                    return true;
                }

                int itemAmount = handItem.getAmount();
                double pricePerItem = getItemValue(handItem);
                double totalPrice = pricePerItem * itemAmount;

                if (totalPrice <= 0) {
                    player.sendMessage(ChatColor.RED + "Dieses Item kann nicht verkauft werden!");
                    return true;
                }

                player.getInventory().setItemInMainHand(null);
                EconomyManager.addMoney(player, totalPrice);
                player.sendMessage(ChatColor.GREEN + "Du hast " + itemAmount + "x " + handItem.getType().name() + " für $" + (int)totalPrice + " verkauft!");
                break;

            case "setrank":
                if (!player.hasPermission("chickensmp.admin")) {
                    player.sendMessage(ChatColor.RED + "Dazu hast du keine Rechte!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /setrank <Spieler> <Rang>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                    return true;
                }

                String rankName = args[1];
                RankManager.setRank(target, rankName);
                player.sendMessage(ChatColor.GREEN + "Rang von " + target.getName() + " auf " + rankName + " gesetzt!");
                target.sendMessage(ChatColor.GOLD + "Dein Rang wurde auf " + rankName + " geändert!");
                break;

            case "pay":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /pay <Spieler> <Betrag>");
                    return true;
                }
                try {
                    double betrag = Double.parseDouble(args[1]);
                    if (EconomyManager.removeMoney(player, betrag)) {
                        player.sendMessage(ChatColor.GREEN + "Du hast $" + (int)betrag + " an " + args[0] + " überwiesen.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld!");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Ungültiger Betrag!");
                }
                break;

            case "adminpay":
                if (!RankManager.getRank(player).equalsIgnoreCase("developer")) {
                    player.sendMessage(ChatColor.RED + "Dieser Befehl ist ausschließlich für Developer reserviert!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Verwendung: /adminpay <Spieler> <Betrag>");
                    return true;
                }
                Player targetAdminPay = Bukkit.getPlayer(args[0]);
                if (targetAdminPay == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden!");
                    return true;
                }

                try {
                    double adminPayAmount = Double.parseDouble(args[1]);
                    EconomyManager.addMoney(targetAdminPay, adminPayAmount);
                    player.sendMessage(ChatColor.GREEN + "Du hast " + targetAdminPay.getName() + " $" + (int)adminPayAmount + " gutgeschrieben!");
                    targetAdminPay.sendMessage(ChatColor.GOLD + "[Developer Admin] Dir wurden $" + (int)adminPayAmount + " gutgeschrieben!");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Ungültiger Betrag!");
                }
                break;
        }
        return true;
    }

    public static double getItemValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;

        Double customPrice = MetaManager.getCustomPrice(item);
        if (customPrice != null) {
            return customPrice;
        }

        return getItemValue(item.getType());
    }

    public static double getItemValue(Material type) {
        switch (type) {
            case NETHER_STAR -> { return 5000.0; }
            case ENCHANTED_GOLDEN_APPLE -> { return 2500.0; }
            case TOTEM_OF_UNDYING -> { return 1500.0; }
            case NETHERITE_INGOT -> { return 1200.0; }
            case SHULKER_SHELL, ELYTRA -> { return 500.0; }
            case ANCIENT_DEBRIS, NETHERITE_SCRAP -> { return 300.0; }
            case GOLDEN_APPLE -> { return 150.0; }
            case DIAMOND -> { return 100.0; }
            case GHAST_TEAR, EMERALD -> { return 75.0; }
            case OBSIDIAN, CRYING_OBSIDIAN -> { return 40.0; }
            case BLAZE_ROD, GOLD_INGOT -> { return 30.0; }
            case ENDER_PEARL -> { return 20.0; }
            case IRON_INGOT -> { return 15.0; }
            case COPPER_INGOT, AMETHYST_SHARD -> { return 8.0; }
            case COAL, LAPIS_LAZULI, REDSTONE -> { return 5.0; }

            case WHEAT, CARROT, POTATO, BEETROOT, SUGAR_CANE -> { return 3.0; }
            case BREAD, COOKED_BEEF, COOKED_PORKCHOP, COOKED_CHICKEN -> { return 6.0; }
            case APPLE -> { return 8.0; }

            case ROTTEN_FLESH -> { return 1.5; }
            case BONE, STRING, GUNPOWDER, SPIDER_EYE -> { return 4.0; }
            case SLIME_BALL -> { return 15.0; }

            case DIRT, COBBLESTONE, STONE, SAND, GRAVEL, NETHERRACK -> { return 1.0; }
            case DEEPSLATE, TUFF, CALCITE, BASALT, BLACKSTONE -> { return 1.5; }
            case GLASS, END_STONE -> { return 3.0; }
            default -> {}
        }

        String name = type.name();

        if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD")) return 10.0;
        if (name.endsWith("_PLANKS")) return 2.5;
        if (name.endsWith("_STAIRS")) return 3.0;
        if (name.endsWith("_SLAB")) return 1.25;

        if (name.contains("DEEPSLATE_") && name.endsWith("_ORE")) return 20.0;
        if (name.endsWith("_ORE")) return 15.0;
        if (name.endsWith("_BLOCK")) {
            if (name.contains("NETHERITE")) return 10800.0;
            if (name.contains("DIAMOND")) return 900.0;
            if (name.contains("EMERALD")) return 675.0;
            if (name.contains("GOLD")) return 270.0;
            if (name.contains("IRON")) return 135.0;
            if (name.contains("COPPER") || name.contains("LAPIS") || name.contains("REDSTONE")) return 50.0;
            if (name.contains("COAL")) return 45.0;
            return 10.0;
        }

        if (name.contains("NETHERITE_")) return 1300.0;
        if (name.contains("DIAMOND_")) return 250.0;
        if (name.contains("GOLDEN_")) return 70.0;
        if (name.contains("IRON_")) return 40.0;
        if (name.contains("CHAINMAIL_")) return 30.0;
        if (name.contains("LEATHER_") || name.contains("STONE_")) return 10.0;
        if (name.contains("WOODEN_")) return 5.0;

        if (name.contains("POTION") || name.contains("TIPPED_ARROW")) return 25.0;
        if (type == Material.ENCHANTED_BOOK) return 150.0;
        if (name.startsWith("MUSIC_DISC_")) return 300.0;

        return 2.0;
    }
}