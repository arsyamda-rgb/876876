package dev.lone.itemsadder.api.Events;
/** STAND-IN, not ItemsAdder. getAction() is deliberately absent: it was added in ItemsAdder 4.0.17 (issue #5375), not in 4.0.3. */
public class FurnitureInteractEvent extends org.bukkit.event.player.PlayerEvent implements org.bukkit.event.Cancellable {
  public boolean isCancelled(){return false;} public void setCancelled(boolean c){}
  public dev.lone.itemsadder.api.CustomFurniture getFurniture(){return null;} public String getNamespacedID(){return null;} public org.bukkit.entity.Entity getBukkitEntity(){return null;} }
