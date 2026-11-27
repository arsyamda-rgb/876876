package dev.lone.itemsadder.api.Events;
public class FurnitureBreakEvent extends org.bukkit.event.player.PlayerEvent implements org.bukkit.event.Cancellable {
  public boolean isCancelled(){return false;} public void setCancelled(boolean c){}
  public dev.lone.itemsadder.api.CustomFurniture getFurniture(){return null;} public String getNamespacedID(){return null;} public org.bukkit.entity.Entity getBukkitEntity(){return null;} }
