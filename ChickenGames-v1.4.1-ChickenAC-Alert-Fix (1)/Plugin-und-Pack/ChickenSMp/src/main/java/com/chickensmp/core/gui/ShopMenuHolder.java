package com.chickensmp.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies shop inventories without relying on translated or resource-pack titles. */
public final class ShopMenuHolder implements InventoryHolder {
    public enum Type { MAIN, CATEGORY }

    private final Type type;
    private final String categoryId;
    private Inventory inventory;

    public ShopMenuHolder(Type type, String categoryId) {
        this.type = type;
        this.categoryId = categoryId;
    }

    public Type getType() {
        return type;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
