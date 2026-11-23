package com.chickensmp.core.manager;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class MetaManager {

    private static final NamespacedKey PRICE_KEY = new NamespacedKey("chickensmp", "custom_price");

    public static void setCustomPrice(ItemStack item, double price) {
        if (item == null || item.getItemMeta() == null) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(PRICE_KEY, PersistentDataType.DOUBLE, price);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("Wert:") || line.contains("Verkaufswert:"));
        lore.add(ChatColor.GRAY + "Wert: " + ChatColor.GREEN + "$" + String.format("%.2f", price) + " (Custom)");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static Double getCustomPrice(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(PRICE_KEY, PersistentDataType.DOUBLE)) {
            return container.get(PRICE_KEY, PersistentDataType.DOUBLE);
        }
        return null;
    }
}