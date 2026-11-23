package com.chickensmp.core.listeners;

import com.chickensmp.core.ChickenSMP;
import com.chickensmp.core.gui.GUIManager;
import com.chickensmp.core.manager.EconomyManager;
import com.chickensmp.core.manager.HomeManager;
import com.chickensmp.core.manager.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Random;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        org.bukkit.inventory.Inventory topInventory = event.getView().getTopInventory();
        // Shop-Menüs werden über ihren eigenen Holder erkannt. Titel können durch
        // Resource-Pack-Zeichen/Farbcodes variieren und waren eine Fehlerquelle.
        String categoryId = GUIManager.getShopCategoryId(topInventory);
        boolean mainShop = GUIManager.isMainShop(topInventory);
        boolean managedGui = mainShop || categoryId != null || title.contains("AUKTIONEN")
                || title.contains("ORDERS") || title.contains("Deine Homes") || title.contains("Random Teleport");
        if (!managedGui) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        String displayName = clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";

        // Shop navigation is based on configured category slots and configured back button.
        if (categoryId != null && GUIManager.isBackButton(event.getSlot(), clicked)) {
            GUIManager.openShop(player);
            return;
        }

        if (mainShop) {
            String selectedCategory = GUIManager.getCategoryAtSlot(event.getSlot());
            if (selectedCategory != null) GUIManager.openCategoryShop(player, selectedCategory);
            return;
        }

        if (categoryId != null) {
            ConfigurationSection product = GUIManager.getShopItemAtSlot(categoryId, event.getSlot());
            if (product != null) purchaseShopItem(player, product);
            return;
        }

        // Homes menu: left click teleports, right click deletes.
        if (title.contains("Deine Homes")) {
            if (clicked.getType() != Material.RED_BED || displayName.isBlank()) return;
            String homeName = displayName.toLowerCase();
            if (event.isRightClick()) {
                if (HomeManager.delHome(player, homeName)) {
                    player.sendMessage(ChatColor.RED + "Home '" + homeName + "' wurde gelöscht.");
                }
                GUIManager.openHomesGUI(player);
            } else {
                Location loc = HomeManager.getHome(player, homeName);
                if (loc != null) {
                    player.closeInventory();
                    TeleportManager.startTeleportCountdown(player, loc, null);
                } else {
                    player.sendMessage(ChatColor.RED + "Dieses Home wurde nicht gefunden.");
                    GUIManager.openHomesGUI(player);
                }
            }
            return;
        }

        // Only the dedicated Random Teleport menu teleports on Ender Pearl click.
        if (title.contains("Random Teleport") && event.getSlot() == 4 && clicked.getType() == Material.ENDER_PEARL) {
            player.closeInventory();
            Random random = new Random();
            int minRadius = Math.max(0, ChickenSMP.getInstance().getConfig().getInt("teleport.rtp.min-radius", 100));
            int maxRadius = Math.max(minRadius, ChickenSMP.getInstance().getConfig().getInt("teleport.rtp.max-radius", 1000));
            maxRadius = Math.min(maxRadius, 100000);
            int distanceX = random.nextInt(maxRadius - minRadius + 1) + minRadius;
            int distanceZ = random.nextInt(maxRadius - minRadius + 1) + minRadius;
            int x = random.nextBoolean() ? distanceX : -distanceX;
            int z = random.nextBoolean() ? distanceZ : -distanceZ;

            String worldName = ChickenSMP.getInstance().getConfig().getString("teleport.rtp.world", "");
            World world = worldName == null || worldName.isBlank() ? player.getWorld() : Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Die konfigurierte RTP-Welt wurde nicht gefunden.");
                return;
            }
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location target = new Location(world, x + 0.5, y, z + 0.5);
            TeleportManager.startTeleportCountdown(player, target, null);
        }
    }

    private void purchaseShopItem(Player player, ConfigurationSection product) {
        String permission = product.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            String denied = ChickenSMP.getInstance().getConfig().getString(
                    "messages.shop-no-permission", "&cDu darfst dieses Item nicht kaufen.");
            player.sendMessage(color(denied));
            GUIManager.playShopSound(player, "shop.sounds.error");
            return;
        }

        String materialName = product.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            player.sendMessage(ChatColor.RED + "Dieses Shop-Item hat eine ungültige Material-Einstellung.");
            return;
        }

        int amount = Math.max(1, Math.min(64, product.getInt("amount", 1)));
        double price = product.getDouble("price", 0.0);
        if (!Double.isFinite(price) || price < 0.0) {
            player.sendMessage(ChatColor.RED + "Dieses Shop-Item hat einen ungültigen Preis.");
            return;
        }

        if (!EconomyManager.removeMoney(player, price)) {
            String message = ChickenSMP.getInstance().getConfig().getString(
                    "messages.shop-not-enough-money", "&cDu hast nicht genug Geld! Preis: {price}");
            player.sendMessage(color(message.replace("{price}", GUIManager.getCurrencySymbol() + formatPrice(price))));
            GUIManager.playShopSound(player, "shop.sounds.error");
            return;
        }

        ItemStack purchased = new ItemStack(material, amount);
        org.bukkit.inventory.meta.ItemMeta purchasedMeta = purchased.getItemMeta();
        if (purchasedMeta != null) {
            String customItemName = product.getString("item-display-name", "");
            if (customItemName != null && !customItemName.isBlank()) {
                purchasedMeta.setDisplayName(color(customItemName));
            }
            java.util.List<String> customItemLore = product.getStringList("item-lore");
            if (!customItemLore.isEmpty()) {
                purchasedMeta.setLore(customItemLore.stream().map(this::color).toList());
            }
            purchased.setItemMeta(purchasedMeta);
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(purchased);
        if (!overflow.isEmpty()) {
            if (ChickenSMP.getInstance().getConfig().getBoolean("shop.drop-overflow", true)) {
                for (ItemStack leftover : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                player.sendMessage(color(ChickenSMP.getInstance().getConfig().getString(
                        "messages.shop-overflow-dropped", "&eDein Inventar war voll. Übrige Items wurden neben dir abgelegt.")));
            } else {
                // Refund if the server owner has disabled overflow drops.
                EconomyManager.addMoney(player, price);
                player.sendMessage(color(ChickenSMP.getInstance().getConfig().getString(
                        "messages.shop-inventory-full", "&cDein Inventar ist voll. Kauf abgebrochen.")));
                return;
            }
        }

        String configuredName = product.getString("name", material.name());
        String displayName = ChatColor.stripColor(color(configuredName));
        String message = ChickenSMP.getInstance().getConfig().getString(
                "messages.shop-purchased", "&aDu hast {amount}x {item} für {price} gekauft.");
        player.sendMessage(color(message
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", displayName)
                .replace("{price}", GUIManager.getCurrencySymbol() + formatPrice(price))));
        GUIManager.playShopSound(player, "shop.sounds.purchase");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) return String.format(java.util.Locale.GERMANY, "%.0f", price);
        return String.format(java.util.Locale.GERMANY, "%.2f", price);
    }
}
