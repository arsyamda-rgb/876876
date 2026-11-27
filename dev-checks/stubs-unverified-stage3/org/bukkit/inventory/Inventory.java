package org.bukkit.inventory;
public interface Inventory { void clear(); void setItem(int s, ItemStack i); InventoryHolder getHolder(); java.util.HashMap<Integer,ItemStack> addItem(ItemStack... i); }
