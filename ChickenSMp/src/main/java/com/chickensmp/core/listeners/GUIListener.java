package com.chickensmp.core.listeners;

import com.chickensmp.core.gui.GUIManager;
import com.chickensmp.core.manager.HomeManager;
import com.chickensmp.core.manager.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Random;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Shop") || title.contains("Nether") || title.contains("Ausrüstung") || title.contains("Essen") ||
                title.contains("AUKTIONEN") || title.contains("ORDERS") || title.contains("Deine Homes") || title.contains("Random Teleport")) {

            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            Player player = (Player) event.getWhoClicked();

            // Zurück zum Hauptshop
            if (event.getCurrentItem().getType() == Material.BARRIER && ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).contains("Zurück zum Shop")) {
                GUIManager.openShop(player);
                return;
            }

            if (title.equals(ChatColor.DARK_GRAY + "Shop")) {
                if (event.getSlot() == 12) GUIManager.openNetherShop(player);
                if (event.getSlot() == 13) GUIManager.openEquipmentShop(player);
                if (event.getSlot() == 14) GUIManager.openFoodShop(player);
                return;
            }

            if (title.equals(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Deine Homes")) {
                if (event.getCurrentItem().getType() == Material.RED_BED) {
                    String rawName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).toLowerCase();
                    if (event.isRightClick()) {
                        HomeManager.delHome(player, rawName);
                        player.sendMessage(ChatColor.RED + "Home '" + rawName + "' wurde gelöscht.");
                        GUIManager.openHomesGUI(player);
                    } else {
                        Location loc = HomeManager.getHome(player, rawName);
                        if (loc != null) {
                            player.closeInventory();
                            TeleportManager.startTeleportCountdown(player, loc, null);
                        }
                    }
                }
                return;
            }

            if (event.getCurrentItem().getType() == Material.ENDER_PEARL) {
                player.closeInventory();
                Random random = new Random();
                int x = random.nextInt(2000) - 1000;
                int z = random.nextInt(2000) - 1000;
                int y = player.getWorld().getHighestBlockYAt(x, z) + 1;
                Location target = new Location(player.getWorld(), x, y, z);
                TeleportManager.startTeleportCountdown(player, target, null);
            }
        }
    }
}