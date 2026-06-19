package dev.loyaltide;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Patches the vanilla anvil so that Loyalty and Riptide can be combined
 * onto a single trident.
 *
 * Vanilla prevents this by marking the two enchantments as conflicting.
 * We intercept PrepareAnvilEvent at HIGHEST priority (after all other plugins),
 * detect the situation where vanilla would block the combine, and manually
 * construct the correct output item.
 *
 * We only act when BOTH inputs involve a trident and would result in
 * Loyalty + Riptide coexisting. All other anvil operations pass through
 * untouched.
 */
public final class AnvilListener implements Listener {

    // Cost cap we allow (mirrors vanilla "too expensive" threshold).
    // Set high so enchant books with high levels still work.
    private static final int MAX_REPAIR_COST = 39;

    private final LoyalTidePlugin plugin;

    public AnvilListener(LoyalTidePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack left  = anvil.getItem(0);   // base item
        ItemStack right = anvil.getItem(1);   // ingredient

        // We only care about trident + something combos.
        if (left == null || left.getType() != Material.TRIDENT) return;
        if (right == null) return;

        // Gather enchantments that WOULD be applied from the right item.
        Map<Enchantment, Integer> incomingEnchants = getEnchants(right);
        if (incomingEnchants.isEmpty()) return;

        // Check if the combination would produce Loyalty + Riptide together.
        Map<Enchantment, Integer> currentEnchants = getEnchants(left);

        boolean willHaveLoyalty  = willHave(currentEnchants, incomingEnchants, Enchantment.LOYALTY);
        boolean willHaveRiptide  = willHave(currentEnchants, incomingEnchants, Enchantment.RIPTIDE);

        if (!willHaveLoyalty || !willHaveRiptide) return;

        // At this point we know the result should have both enchantments.
        // Vanilla may have set the result to null (blocked) or produced a
        // partial result. Either way, we build the correct output ourselves.

        ItemStack result = left.clone();
        ItemMeta  meta   = result.getItemMeta();
        if (meta == null) return;

        // Merge ALL enchantments from right into result, overriding conflicts.
        for (Map.Entry<Enchantment, Integer> entry : incomingEnchants.entrySet()) {
            Enchantment enc   = entry.getKey();
            int         level = entry.getValue();

            int currentLevel = meta.getEnchants().getOrDefault(enc, 0);

            if (level > currentLevel) {
                // Higher level wins (vanilla rule).
                meta.addEnchant(enc, level, true /* ignoreRestrictions */);
            } else if (level == currentLevel && enc.equals(Enchantment.LOYALTY)
                    || level == currentLevel && enc.equals(Enchantment.RIPTIDE)) {
                // Same level: keep existing (vanilla behaviour).
                // For other enchants: same level keeps existing too.
                meta.addEnchant(enc, currentLevel, true);
            } else {
                // lower level incoming – keep existing
                meta.addEnchant(enc, currentLevel, true);
            }
        }

        result.setItemMeta(meta);

        // Set an XP cost. We mimic vanilla's cost calculation roughly.
        int cost = computeCost(currentEnchants, incomingEnchants);
        anvil.setRepairCost(Math.min(cost, MAX_REPAIR_COST));

        event.setResult(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns all enchantments on an item, whether stored (enchanted book)
     * or directly applied.
     */
    private static Map<Enchantment, Integer> getEnchants(ItemStack item) {
        if (item == null) return Map.of();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Map.of();

        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            return new HashMap<>(bookMeta.getStoredEnchants());
        }
        return new HashMap<>(meta.getEnchants());
    }

    /**
     * Returns true if the combined map of base + incoming would have the
     * given enchantment at level >= 1.
     */
    private static boolean willHave(
            Map<Enchantment, Integer> base,
            Map<Enchantment, Integer> incoming,
            Enchantment enchantment) {
        return base.getOrDefault(enchantment, 0) >= 1
                || incoming.getOrDefault(enchantment, 0) >= 1;
    }

    /**
     * Simple cost: 1 level per enchantment being added / upgraded.
     * Keeps costs low so "too expensive" is avoided.
     */
    private static int computeCost(
            Map<Enchantment, Integer> base,
            Map<Enchantment, Integer> incoming) {
        int cost = 0;
        for (Map.Entry<Enchantment, Integer> e : incoming.entrySet()) {
            int existingLevel = base.getOrDefault(e.getKey(), 0);
            if (e.getValue() > existingLevel) {
                cost += e.getValue();
            } else if (e.getValue() == existingLevel) {
                cost += e.getValue(); // same level: upgrade by +1 conceptually
            } else {
                cost += 1; // lower level: minimal cost
            }
        }
        return Math.max(1, cost);
    }
}
