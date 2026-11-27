package org.bukkit.event.inventory;
public class InventoryClickEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
  public boolean isCancelled(){return false;} public void setCancelled(boolean c){}
  public org.bukkit.entity.Entity getWhoClicked(){return null;} public org.bukkit.inventory.Inventory getClickedInventory(){return null;} public int getSlot(){return 0;} }
