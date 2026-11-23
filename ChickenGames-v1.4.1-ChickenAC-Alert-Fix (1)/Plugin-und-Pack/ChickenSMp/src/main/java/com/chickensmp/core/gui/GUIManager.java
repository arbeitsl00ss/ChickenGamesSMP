package com.chickensmp.core.gui;

import com.chickensmp.core.ChickenSMP;
import com.chickensmp.core.manager.HomeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All shop categories and products are read from config.yml. */
public class GUIManager {

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static int menuSize(int configured) {
        int size = Math.max(9, Math.min(54, configured));
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    private static ConfigurationSection shopConfig() {
        return ChickenSMP.getInstance().getConfig().getConfigurationSection("shop");
    }

    public static String getMainShopTitle() {
        String title = ChickenSMP.getInstance().getConfig().getString("shop.title", "&fShop");
        return GuiTitles.rows3(color(title));
    }

    public static boolean isMainShop(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ShopMenuHolder holder
                && holder.getType() == ShopMenuHolder.Type.MAIN;
    }

    public static String getShopCategoryId(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof ShopMenuHolder holder)) return null;
        return holder.getType() == ShopMenuHolder.Type.CATEGORY ? holder.getCategoryId() : null;
    }

    private static Inventory createShopInventory(ShopMenuHolder.Type type, String categoryId, int size, String title) {
        ShopMenuHolder holder = new ShopMenuHolder(type, categoryId);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    public static String getCategoryIdFromTitle(String inventoryTitle) {
        ConfigurationSection categories = ChickenSMP.getInstance().getConfig().getConfigurationSection("shop.categories");
        if (categories == null) return null;
        for (String id : categories.getKeys(false)) {
            ConfigurationSection category = categories.getConfigurationSection(id);
            if (category == null || !category.getBoolean("enabled", true)) continue;
            String configuredTitle = category.getString("title", "&b" + id);
            if (GuiTitles.rows3(color(configuredTitle)).equals(inventoryTitle)) return id;
        }
        return null;
    }

    public static String getCategoryAtSlot(int slot) {
        ConfigurationSection categories = ChickenSMP.getInstance().getConfig().getConfigurationSection("shop.categories");
        if (categories == null) return null;
        for (String id : categories.getKeys(false)) {
            ConfigurationSection category = categories.getConfigurationSection(id);
            if (category != null && category.getBoolean("enabled", true)
                    && category.getInt("menu-slot", -1) == slot) return id;
        }
        return null;
    }

    public static ConfigurationSection getShopItemAtSlot(String categoryId, int slot) {
        ConfigurationSection items = ChickenSMP.getInstance().getConfig()
                .getConfigurationSection("shop.categories." + categoryId + ".items");
        if (items == null) return null;
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.getBoolean("enabled", true) && item.getInt("slot", -1) == slot) {
                return item;
            }
        }
        return null;
    }

    public static String getCurrencySymbol() {
        return ChickenSMP.getInstance().getConfig().getString("economy.currency-symbol", "$" );
    }

    public static boolean isBackButton(int slot, ItemStack item) {
        if (item == null) return false;
        ConfigurationSection back = ChickenSMP.getInstance().getConfig().getConfigurationSection("shop.back-button");
        if (back != null && !back.getBoolean("enabled", true)) return false;
        int configuredSlot = ChickenSMP.getInstance().getConfig().getInt("shop.back-button.slot", 26);
        Material configuredMaterial = Material.matchMaterial(
                ChickenSMP.getInstance().getConfig().getString("shop.back-button.material", "BARRIER"));
        return slot == configuredSlot && (configuredMaterial == null || item.getType() == configuredMaterial);
    }

    public static void openShop(Player player) {
        ConfigurationSection shop = shopConfig();
        if (shop != null && !shop.getBoolean("enabled", true)) {
            player.sendMessage(color(ChickenSMP.getInstance().getConfig()
                    .getString("messages.shop-disabled", "&cDer Shop ist momentan deaktiviert.")));
            return;
        }

        int size = menuSize(ChickenSMP.getInstance().getConfig().getInt("shop.size", 27));
        Inventory inventory = createShopInventory(ShopMenuHolder.Type.MAIN, null, size, getMainShopTitle());
        ConfigurationSection categories = ChickenSMP.getInstance().getConfig().getConfigurationSection("shop.categories");
        if (categories != null) {
            for (String id : categories.getKeys(false)) {
                ConfigurationSection category = categories.getConfigurationSection(id);
                if (category == null || !category.getBoolean("enabled", true)) continue;
                String permission = category.getString("permission", "");
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
                int slot = category.getInt("menu-slot", -1);
                Material material = Material.matchMaterial(category.getString("icon", "CHEST"));
                if (slot < 0 || slot >= size) {
                    ChickenSMP.getInstance().getLogger().warning("Shop-Kategorie '" + id + "' hat einen ungültigen menu-slot: " + slot);
                    continue;
                }
                if (material == null || material.isAir()) {
                    ChickenSMP.getInstance().getLogger().warning("Shop-Kategorie '" + id + "' hat ein ungültiges icon-Material.");
                    continue;
                }
                String name = color(category.getString("name", "&b" + id));
                inventory.setItem(slot, createGuiItem(material, name, colorList(category.getStringList("lore"))));
            }
        }
        player.openInventory(inventory);
        playShopSound(player, "shop.sounds.open");
    }

    public static void openCategoryShop(Player player, String categoryId) {
        ConfigurationSection category = ChickenSMP.getInstance().getConfig()
                .getConfigurationSection("shop.categories." + categoryId);
        if (category == null || !category.getBoolean("enabled", true)) {
            player.sendMessage(color(ChickenSMP.getInstance().getConfig()
                    .getString("messages.shop-category-disabled", "&cDiese Shop-Kategorie ist deaktiviert.")));
            return;
        }
        String categoryPermission = category.getString("permission", "");
        if (categoryPermission != null && !categoryPermission.isBlank() && !player.hasPermission(categoryPermission)) {
            player.sendMessage(color(ChickenSMP.getInstance().getConfig()
                    .getString("messages.shop-category-no-permission", "&cDu darfst diese Kategorie nicht öffnen.")));
            return;
        }

        int size = menuSize(category.getInt("size", ChickenSMP.getInstance().getConfig().getInt("shop.category-size", 27)));
        String title = GuiTitles.rows3(color(category.getString("title", "&b" + categoryId)));
        Inventory inventory = createShopInventory(ShopMenuHolder.Type.CATEGORY, categoryId, size, title);

        ConfigurationSection items = category.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection product = items.getConfigurationSection(key);
                if (product == null || !product.getBoolean("enabled", true)) continue;
                String permission = product.getString("permission", "");
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
                int slot = product.getInt("slot", -1);
                Material material = Material.matchMaterial(product.getString("material", "STONE"));
                int amount = Math.max(1, Math.min(64, product.getInt("amount", 1)));
                if (slot < 0 || slot >= size) {
                    ChickenSMP.getInstance().getLogger().warning("Shop-Produkt '" + categoryId + "." + key + "' hat einen ungültigen Slot: " + slot);
                    continue;
                }
                if (material == null || material.isAir()) {
                    ChickenSMP.getInstance().getLogger().warning("Shop-Produkt '" + categoryId + "." + key + "' hat ein ungültiges Material: " + product.getString("material"));
                    continue;
                }

                String name = color(product.getString("name", key));
                List<String> lore = colorList(product.getStringList("lore"));
                double price = Math.max(0.0, product.getDouble("price", 0.0));
                String priceLine = ChickenSMP.getInstance().getConfig().getString(
                        "shop.display.price-line", "&ePreis: {currency}{price}");
                String clickLine = ChickenSMP.getInstance().getConfig().getString(
                        "shop.display.click-line", "&7Klicke zum Kaufen");
                lore.add(color(priceLine.replace("{currency}", getCurrencySymbol())
                        .replace("{price}", formatPrice(price))
                        .replace("{amount}", String.valueOf(amount))));
                if (!clickLine.isBlank()) lore.add(color(clickLine));
                inventory.setItem(slot, createGuiItem(material, name, lore, amount));
            }
        }

        ConfigurationSection back = ChickenSMP.getInstance().getConfig().getConfigurationSection("shop.back-button");
        if (back == null || back.getBoolean("enabled", true)) {
            int backSlot = back == null ? 26 : back.getInt("slot", 26);
            Material backMaterial = Material.matchMaterial(back == null ? "BARRIER" : back.getString("material", "BARRIER"));
            String backName = color(back == null ? "&cZurück zum Shop" : back.getString("name", "&cZurück zum Shop"));
            if (backMaterial != null && !backMaterial.isAir() && backSlot >= 0 && backSlot < size) {
                List<String> backLore = colorList(back == null
                        ? List.of("&7Klicke zum Hauptshop") : back.getStringList("lore"));
                inventory.setItem(backSlot, createGuiItem(backMaterial, backName, backLore));
            }
        }
        player.openInventory(inventory);
        playShopSound(player, "shop.sounds.open");
    }

    public static void playShopSound(Player player, String configPath) {
        String soundName = ChickenSMP.getInstance().getConfig().getString(configPath, "");
        if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("NONE")) return;
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName.toUpperCase(Locale.ROOT)), 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            ChickenSMP.getInstance().getLogger().warning("Unbekannter Sound in config.yml: " + soundName + " (" + configPath + ")");
        }
    }

    private static List<String> colorList(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) result.add(color(line));
        return result;
    }

    private static String formatPrice(double price) {
        if (price == Math.rint(price)) return String.format(Locale.GERMANY, "%.0f", price);
        return String.format(Locale.GERMANY, "%.2f", price);
    }

    public static void openNetherShop(Player player) { openCategoryShop(player, "nether"); }
    public static void openEquipmentShop(Player player) { openCategoryShop(player, "equipment"); }
    public static void openFoodShop(Player player) { openCategoryShop(player, "food"); }
    public static void openSpecialsShop(Player player) { openCategoryShop(player, "specials"); }

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

    public static void openOrderGUI(Player player) { openOrders(player); }

    public static void openHomesGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GuiTitles.rows3(ChatColor.AQUA + "" + ChatColor.BOLD + "Deine Homes"));
        List<Map.Entry<String, org.bukkit.Location>> homes = new ArrayList<>(HomeManager.getHomes(player).entrySet());
        homes.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

        if (homes.isEmpty()) {
            gui.setItem(13, createGuiItem(Material.BARRIER, ChatColor.RED + "Noch keine Homes",
                    List.of(ChatColor.GRAY + "Setze ein Home mit /sethome <Name>",
                            ChatColor.GRAY + "Öffne deine Homes später mit /homes")));
        } else {
            int slot = 0;
            for (Map.Entry<String, org.bukkit.Location> entry : homes) {
                if (slot >= 27) break;
                String homeName = entry.getKey();
                gui.setItem(slot++, createGuiItem(Material.RED_BED, ChatColor.GREEN + homeName,
                        List.of(ChatColor.GRAY + "Linksklick: Teleportieren",
                                ChatColor.GRAY + "Rechtsklick: Home löschen",
                                ChatColor.YELLOW + "/home " + homeName)));
            }
        }
        player.openInventory(gui);
    }

    public static void openRTP(Player player) {
        Inventory rtp = Bukkit.createInventory(null, 9, GuiTitles.rows1(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Random Teleport"));
        rtp.setItem(4, createGuiItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "Zufällig teleportieren", null));
        player.openInventory(rtp);
    }

    private static ItemStack createGuiItem(Material material, String name, List<String> lore) {
        return createGuiItem(material, name, lore, 1);
    }

    private static ItemStack createGuiItem(Material material, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
