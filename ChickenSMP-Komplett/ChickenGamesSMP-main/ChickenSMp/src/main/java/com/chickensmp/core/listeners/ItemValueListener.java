package com.chickensmp.core.listeners;

import com.chickensmp.core.commands.ExtendedCommands;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemValueListener implements Listener {

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            updateInventoryItemValues(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        updateInventoryItemValues(event.getPlayer());
    }

    public static void updateInventoryItemValues(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            double unitValue = ExtendedCommands.getItemValue(item);
            double totalValue = unitValue * item.getAmount();

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            // Entfernt alte Werte (egal ob "Wert:" oder "Verkaufswert:")
            lore.removeIf(line -> line.contains("Wert:") || line.contains("Verkaufswert:"));

            if (totalValue > 0) {
                // Angepasst auf das HugoSMP-Design mit "Wert: $XX.XX"
                lore.add(ChatColor.GRAY + "Wert: " + ChatColor.GREEN + "$" + String.format("%.2f", totalValue));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }
}