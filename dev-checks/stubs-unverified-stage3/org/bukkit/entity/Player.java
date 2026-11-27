package org.bukkit.entity;
public interface Player extends Entity, org.bukkit.OfflinePlayer, org.bukkit.command.CommandSender, org.bukkit.inventory.InventoryHolder {
  boolean isOnline(); String getName(); org.bukkit.GameMode getGameMode(); org.bukkit.inventory.PlayerInventory getInventory();
  void closeInventory(); org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory i);
}
