package com.veyrion.wardrobe.mirror;

import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.CustomStack;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * The only class that calls ItemsAdder for furniture and items. Target: ItemsAdder 4.0.3 (not 4.0.17). The calls used
 * here are CustomStack.getInstance(String), CustomStack.isInRegistry(String), CustomStack#getItemStack(),
 * CustomFurniture.byAlreadySpawned(Entity) and CustomFurniture#remove(boolean). Run
 * dev-checks/verify-itemsadder-api.sh against the real 4.0.3 jar to confirm each one exists.
 */
public final class ItemsAdderBridge {

    /** Is this id a registered ItemsAdder item? Only meaningful after ItemsAdderLoadDataEvent (loading is async). */
    public boolean isRegistered(String namespacedId) {
        return CustomStack.isInRegistry(namespacedId);
    }

    /** A fresh copy of the item for the id, or empty if ItemsAdder does not know it. */
    public Optional<ItemStack> newItem(String namespacedId) {
        CustomStack stack = CustomStack.getInstance(namespacedId);
        return stack == null ? Optional.empty() : Optional.ofNullable(stack.getItemStack());
    }

    /**
     * Removes the furniture that this entity belongs to.
     * @param dropItem true = the furniture item drops (like mining it); false = nothing drops
     * @return false if the entity is not ItemsAdder furniture (already gone)
     */
    public boolean removeFurniture(Entity entity, boolean dropItem) {
        CustomFurniture furniture = CustomFurniture.byAlreadySpawned(entity);
        if (furniture == null) {
            return false;
        }
        furniture.remove(dropItem);
        return true;
    }
}
