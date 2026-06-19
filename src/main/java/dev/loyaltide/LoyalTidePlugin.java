package dev.loyaltide;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * LoyalTide - Allows Loyalty and Riptide to coexist on a Trident.
 *
 * Architecture overview:
 * - AnvilListener:  Patches PrepareAnvilEvent so Loyalty+Riptide can be
 *                   combined in an anvil without being blocked by vanilla
 *                   conflict checks.
 * - TridentListener: Intercepts ProjectileLaunchEvent (Riptide) and
 *                    EntityPickupItemEvent / PlayerFishEvent to override
 *                    vanilla behaviour when a trident has BOTH enchants.
 *
 * No NBT is written to items. No schedulers are used for state polling.
 * All logic is purely event-driven.
 */
public final class LoyalTidePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("LoyalTide enabled - Loyalty + Riptide coexistence active.");

        AnvilListener  anvilListener   = new AnvilListener(this);
        TridentListener tridentListener = new TridentListener(this);

        getServer().getPluginManager().registerEvents(anvilListener,   this);
        getServer().getPluginManager().registerEvents(tridentListener, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("LoyalTide disabled.");
    }
}
