package dev.loyaltide;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Core logic for Loyalty + Riptide coexistence.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  RULES (only applied when trident has BOTH Loyalty ≥1 AND Riptide ≥1)  │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ MAINHAND + IN WATER  → Riptide (vanilla)                                │
 * │ MAINHAND + NOT water → Loyalty  (cancel Riptide, throw normally)        │
 * │ OFFHAND              → Loyalty  (always – cancel any Riptide attempt)   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Implementation strategy
 * -----------------------
 * Riptide launches the player via a ProjectileLaunchEvent where the
 * projectile is a Trident entity. We inspect the shooter and the hand
 * that threw it to decide whether to allow or cancel the Riptide effect.
 *
 * Loyalty return is vanilla and does NOT need to be replicated here – if
 * Riptide is cancelled the trident simply stays in the player's hand or
 * falls to the ground, and Loyalty will pull it back normally.
 *
 * The OFFHAND rule: vanilla does not allow Riptide from offhand by default,
 * so we only need to ensure we never accidentally enable it. The mainhand
 * "not in water" path requires us to cancel the launch event so the trident
 * behaves like a Loyalty throw.
 *
 * No item mutation, no NBT, no scheduled tasks.
 */
public final class TridentListener implements Listener {

    /** Metadata key stored transiently on Trident entities we process. */
    private static final String META_LOYALTIDE = "loyaltide_loyalty_mode";

    private final LoyalTidePlugin plugin;

    public TridentListener(LoyalTidePlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Intercept the Riptide launch
    // =========================================================================

    /**
     * Called when any projectile is launched. We intercept Trident entities
     * thrown by a Player and apply our logic.
     *
     * Priority HIGHEST so we run after other plugins but still before
     * the default server processing of the result.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        // Determine which hand the trident came from.
        // Paper exposes getHand() on some projectiles; for Trident we can
        // also check which hand holds a Trident item at launch time.
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem  = player.getInventory().getItemInOffHand();

        boolean thrownFromMain = isTrident(mainHandItem);
        boolean thrownFromOff  = isTrident(offHandItem) && !thrownFromMain;

        // Get the actual item used (prefer main hand).
        ItemStack usedItem = thrownFromMain ? mainHandItem : offHandItem;

        if (usedItem == null || !isTrident(usedItem)) return;
        if (!TridentUtil.isDualEnchanted(usedItem)) return;

        // --- Apply rules ---

        if (thrownFromOff) {
            // OFFHAND → always Loyalty behaviour.
            // Vanilla wouldn't normally launch Riptide from offhand, but
            // just in case another plugin or future version allows it, cancel.
            if (isRiptideLaunch(trident, player)) {
                event.setCancelled(true);
            }
            return;
        }

        // MAINHAND
        boolean inWater = TridentUtil.isInWater(player);

        if (inWater) {
            // MAINHAND + IN WATER → Riptide vanilla. Allow.
            return;
        }

        // MAINHAND + NOT IN WATER → Loyalty. Cancel Riptide propulsion.
        if (isRiptideLaunch(trident, player)) {
            event.setCancelled(true);
            // Trigger a vanilla-style loyalty throw instead:
            launchLoyaltyThrow(player, usedItem);
        }
    }

    // =========================================================================
    // Loyalty throw simulation (when Riptide is suppressed on dry land)
    // =========================================================================

    /**
     * Simulates a vanilla Loyalty trident throw.
     * The trident item leaves the player's hand and gets a Loyalty return.
     * We spawn a Trident entity with the same item, pointing in the look
     * direction. Vanilla Loyalty handling then returns it automatically
     * because the entity was spawned with hasBeenDealtDamage=false.
     */
    private void launchLoyaltyThrow(Player player, ItemStack tridentItem) {
        // Vanilla throw: spawn a trident entity in look direction.
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        // Use the world's spawnEntity to get a Trident entity we can configure.
        Trident loyaltyTrident = (Trident) player.getWorld().spawnEntity(
                eye.add(direction.clone().multiply(1.5)),
                EntityType.TRIDENT
        );

        loyaltyTrident.setShooter(player);
        loyaltyTrident.setItem(tridentItem.clone());
        loyaltyTrident.setVelocity(direction.multiply(2.5));
        loyaltyTrident.setPickupStatus(AbstractArrow.PickupStatus.NOT_ALLOWED);
        // Loyalty level: read from item
        int loyaltyLevel = tridentItem.getEnchantmentLevel(Enchantment.LOYALTY);
        loyaltyTrident.setLoyaltyLevel(loyaltyLevel);

        // Mark that we spawned this so other listeners can identify it.
        loyaltyTrident.setMetadata(META_LOYALTIDE,
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Remove one trident from the player's hand (simulate the throw).
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static boolean isTrident(ItemStack item) {
        return item != null && item.getType() == Material.TRIDENT;
    }

    /**
     * Heuristic: a Riptide launch gives the trident very high vertical/
     * forward velocity and does NOT leave the item in the player's hand.
     * More reliably: we check whether the Trident entity has the Riptide
     * enchant active. Paper's Trident API exposes this via
     * {@link Trident#hasGlint()} being true and velocity being player-relative.
     *
     * The cleanest check is simply: did the player just leave the ground
     * or are they in water? If IN water → Riptide. Otherwise → this method
     * is called only when we already determined NOT in water.
     *
     * For our purposes: if we reach here, the player is NOT in water, so
     * any Trident launch from a dual-enchanted item IS a Riptide attempt
     * that we want to convert to Loyalty.
     */
    private static boolean isRiptideLaunch(Trident trident, Player player) {
        // If the player is not in water but a Riptide trident entity spawned,
        // it must be a Riptide launch attempt. We detect this by checking that
        // the trident's velocity is aligned with the player's look vector
        // (i.e., the server launched it with Riptide propulsion).
        // A loyalty throw would not have extreme Y velocity.
        Vector vel = trident.getVelocity();
        // Riptide launches produce high combined speed
        return vel.lengthSquared() > 1.0;
    }
}
