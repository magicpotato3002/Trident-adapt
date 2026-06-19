package dev.loyaltide;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Shared utility methods used by both listeners.
 */
public final class TridentUtil {

    private TridentUtil() {}

    /** Returns true if the item has Loyalty level >= 1. */
    public static boolean hasLoyalty(ItemStack item) {
        if (item == null) return false;
        return item.getEnchantmentLevel(Enchantment.LOYALTY) >= 1;
    }

    /** Returns true if the item has Riptide level >= 1. */
    public static boolean hasRiptide(ItemStack item) {
        if (item == null) return false;
        return item.getEnchantmentLevel(Enchantment.RIPTIDE) >= 1;
    }

    /**
     * Returns true if the item carries BOTH Loyalty AND Riptide –
     * i.e. this plugin needs to intercept behaviour for it.
     */
    public static boolean isDualEnchanted(ItemStack item) {
        return hasLoyalty(item) && hasRiptide(item);
    }

    /**
     * Returns the trident the player is currently holding in the MAIN hand,
     * or null if the main-hand item is not a trident.
     */
    public static ItemStack getMainHandTrident(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return null;
        if (item.getType() != org.bukkit.Material.TRIDENT) return null;
        return item;
    }

    /**
     * Returns the trident the player is holding in the OFF hand,
     * or null if the off-hand item is not a trident.
     */
    public static ItemStack getOffHandTrident(Player player) {
        ItemStack item = player.getInventory().getItemInOffHand();
        if (item == null) return null;
        if (item.getType() != org.bukkit.Material.TRIDENT) return null;
        return item;
    }

    /**
     * Checks whether the player is currently in or under water.
     * Uses the block at eye level to handle swimming / submerged states.
     */
    public static boolean isInWater(Player player) {
        // isInWater() checks if the entity is touching water.
        // Also check the block at eye position for full submersion.
        return player.isInWater()
                || player.getEyeLocation().getBlock().isLiquid();
    }
}
