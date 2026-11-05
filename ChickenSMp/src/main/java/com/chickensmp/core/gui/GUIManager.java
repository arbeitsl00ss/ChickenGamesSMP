package com.chickensmp.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

public class GUIManager {

    public static void openShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.WHITE + "Shop"));

        shop.setItem(11, createGuiItem(Material.BARRIER, ChatColor.RED + "Verboten", Collections.singletonList(ChatColor.GRAY + "Kategorie geschlossen")));
        shop.setItem(12, createGuiItem(Material.NETHERRACK, ChatColor.AQUA + "Nether", Collections.singletonList(ChatColor.GRAY + "Klicke um Nether-Items zu kaufen")));
        shop.setItem(13, createGuiItem(Material.TOTEM_OF_UNDYING, ChatColor.AQUA + "Ausrüstung", Collections.singletonList(ChatColor.GRAY + "Klicke für Waffen & Tools")));
        shop.setItem(14, createGuiItem(Material.COOKED_PORKCHOP, ChatColor.AQUA + "Essen", Collections.singletonList(ChatColor.GRAY + "Klicke für Nahrungsmittel")));
        shop.setItem(15, createGuiItem(Material.NETHER_STAR, ChatColor.AQUA + "Köpfe & Specials", Collections.singletonList(ChatColor.GRAY + "Klicke für Deko & Köpfe")));

        player.openInventory(shop);
    }

    public static void openNetherShop(Player player) {
        Inventory nether = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.AQUA + "Nether"));
        nether.setItem(10, createGuiItem(Material.BLAZE_ROD, ChatColor.GOLD + "Blaze Rod", Collections.singletonList(ChatColor.YELLOW + "Preis: $30")));
        nether.setItem(11, createGuiItem(Material.NETHER_WART, ChatColor.RED + "Nether Warze", Collections.singletonList(ChatColor.YELLOW + "Preis: $10")));
        nether.setItem(12, createGuiItem(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Glowstone Staub", Collections.singletonList(ChatColor.YELLOW + "Preis: $15")));
        nether.setItem(13, createGuiItem(Material.MAGMA_CREAM, ChatColor.GOLD + "Magma Creme", Collections.singletonList(ChatColor.YELLOW + "Preis: $25")));
        nether.setItem(14, createGuiItem(Material.GHAST_TEAR, ChatColor.WHITE + "Ghast Träne", Collections.singletonList(ChatColor.YELLOW + "Preis: $75")));
        nether.setItem(15, createGuiItem(Material.QUARTZ, ChatColor.WHITE + "Quarz", Collections.singletonList(ChatColor.YELLOW + "Preis: $12")));
        nether.setItem(16, createGuiItem(Material.SOUL_SAND, ChatColor.DARK_GRAY + "Seelensand", Collections.singletonList(ChatColor.YELLOW + "Preis: $20")));
        nether.setItem(17, createGuiItem(Material.MAGMA_BLOCK, ChatColor.RED + "Magmablock", Collections.singletonList(ChatColor.YELLOW + "Preis: $18")));
        nether.setItem(18, createGuiItem(Material.CRYING_OBSIDIAN, ChatColor.DARK_PURPLE + "Crying Obsidian", Collections.singletonList(ChatColor.YELLOW + "Preis: $40")));

        // Zurück-Button
        nether.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Zurück zum Shop", Collections.singletonList(ChatColor.GRAY + "Klicke zum Hauptshop")));
        player.openInventory(nether);
    }

    public static void openEquipmentShop(Player player) {
        Inventory eq = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.AQUA + "Ausrüstung"));
        eq.setItem(10, createGuiItem(Material.OBSIDIAN, ChatColor.DARK_PURPLE + "Obsidian", Collections.singletonList(ChatColor.YELLOW + "Preis: $40")));
        eq.setItem(11, createGuiItem(Material.GLOWSTONE, ChatColor.YELLOW + "Glowstone", Collections.singletonList(ChatColor.YELLOW + "Preis: $30")));
        eq.setItem(12, createGuiItem(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Totem", Collections.singletonList(ChatColor.YELLOW + "Preis: $1,500")));
        eq.setItem(13, createGuiItem(Material.ENDER_PEARL, ChatColor.DARK_GREEN + "Enderperle", Collections.singletonList(ChatColor.YELLOW + "Preis: $20")));
        eq.setItem(14, createGuiItem(Material.GOLDEN_APPLE, ChatColor.GOLD + "Goldapfel", Collections.singletonList(ChatColor.YELLOW + "Preis: $150")));
        eq.setItem(15, createGuiItem(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "XP-Flasche", Collections.singletonList(ChatColor.YELLOW + "Preis: $50")));
        eq.setItem(16, createGuiItem(Material.ENDER_CHEST, ChatColor.DARK_PURPLE + "Endertruhe", Collections.singletonList(ChatColor.YELLOW + "Preis: $300")));
        eq.setItem(17, createGuiItem(Material.SHIELD, ChatColor.GRAY + "Schild", Collections.singletonList(ChatColor.YELLOW + "Preis: $100")));
        eq.setItem(18, createGuiItem(Material.ARROW, ChatColor.WHITE + "Pfeile", Collections.singletonList(ChatColor.YELLOW + "Preis: $5")));

        // Zurück-Button
        eq.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Zurück zum Shop", Collections.singletonList(ChatColor.GRAY + "Klicke zum Hauptshop")));
        player.openInventory(eq);
    }

    public static void openFoodShop(Player player) {
        Inventory food = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.AQUA + "Essen"));
        food.setItem(10, createGuiItem(Material.POTATO, ChatColor.GOLD + "Kartoffel", Collections.singletonList(ChatColor.YELLOW + "Preis: $3")));
        food.setItem(11, createGuiItem(Material.SWEET_BERRIES, ChatColor.RED + "Beeren", Collections.singletonList(ChatColor.YELLOW + "Preis: $2")));
        food.setItem(12, createGuiItem(Material.MELON_SLICE, ChatColor.GREEN + "Melone", Collections.singletonList(ChatColor.YELLOW + "Preis: $3")));
        food.setItem(13, createGuiItem(Material.CARROT, ChatColor.GOLD + "Karotte", Collections.singletonList(ChatColor.YELLOW + "Preis: $3")));
        food.setItem(14, createGuiItem(Material.BREAD, ChatColor.YELLOW + "Brot", Collections.singletonList(ChatColor.YELLOW + "Preis: $6")));
        food.setItem(15, createGuiItem(Material.APPLE, ChatColor.RED + "Apfel", Collections.singletonList(ChatColor.YELLOW + "Preis: $8")));
        food.setItem(16, createGuiItem(Material.COOKED_CHICKEN, ChatColor.GOLD + "Hähnchen", Collections.singletonList(ChatColor.YELLOW + "Preis: $6")));
        food.setItem(17, createGuiItem(Material.COOKED_BEEF, ChatColor.GOLD + "Steak", Collections.singletonList(ChatColor.YELLOW + "Preis: $6")));

        // Zurück-Button
        food.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Zurück zum Shop", Collections.singletonList(ChatColor.GRAY + "Klicke zum Hauptshop")));
        player.openInventory(food);
    }

    public static void openAH(Player player) {
        Inventory ah = Bukkit.createInventory(null, 54, GuiTitles.rows6(ChatColor.WHITE + "AUKTIONEN"));
        ah.setItem(45, createGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Aktualisieren", null));
        ah.setItem(46, createGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Suchen", null));
        ah.setItem(48, createGuiItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "Zurück", null));
        ah.setItem(49, createGuiItem(Material.BLUE_CONCRETE, ChatColor.BLUE + "Filtern", null));
        ah.setItem(50, createGuiItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "Weiter", null));
        ah.setItem(52, createGuiItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "Meine Angebote", null));
        ah.setItem(53, createGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+ Item Verkaufen", null));
        player.openInventory(ah);
    }

    public static void openOrders(Player player) {
        Inventory orders = Bukkit.createInventory(null, 54, GuiTitles.rows6(ChatColor.WHITE + "ORDERS"));
        orders.setItem(45, createGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Aktualisieren", null));
        orders.setItem(46, createGuiItem(Material.RED_CONCRETE, ChatColor.RED + "Suchen", null));
        orders.setItem(48, createGuiItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "Zurück", null));
        orders.setItem(49, createGuiItem(Material.BLUE_CONCRETE, ChatColor.BLUE + "Filtern", null));
        orders.setItem(50, createGuiItem(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA + "Weiter", null));
        orders.setItem(52, createGuiItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "Meine Orders", null));
        orders.setItem(53, createGuiItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+ Neue Order", null));
        player.openInventory(orders);
    }

    public static void openOrderGUI(Player player) {
        openOrders(player);
    }

    public static void openHomesGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.AQUA + "" + ChatColor.BOLD + "Deine Homes"));
        player.openInventory(gui);
    }

    public static void openRTP(Player player) {
        Inventory rtp = Bukkit.createInventory(null, 9, GuiTitles.rows1(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Random Teleport"));
        rtp.setItem(4, createGuiItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Zufällig teleportieren", null));
        player.openInventory(rtp);
    }

    private static ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}