package dev.loyaltide;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class TridentListener implements Listener {

    private int getEnchantLevel(ItemStack item, Enchantment enchant) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchantLevel(enchant);
        }
        return meta.getEnchantLevel(enchant);
    }

    private boolean hasBothEnchants(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT) return false;
        return getEnchantLevel(item, Enchantment.LOYALTY) >= 1 && getEnchantLevel(item, Enchantment.RIPTIDE) >= 1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getFirstItem();
        ItemStack right = inv.getSecondItem();

        if (left == null || right == null) return;

        boolean isLeftValid = left.getType() == Material.TRIDENT || left.getType() == Material.ENCHANTED_BOOK;
        if (!isLeftValid) return;

        int leftLoyalty = getEnchantLevel(left, Enchantment.LOYALTY);
        int leftRiptide = getEnchantLevel(left, Enchantment.RIPTIDE);
        int rightLoyalty = getEnchantLevel(right, Enchantment.LOYALTY);
        int rightRiptide = getEnchantLevel(right, Enchantment.RIPTIDE);

        int finalLoyalty = Math.max(leftLoyalty, rightLoyalty);
        if (leftLoyalty == rightLoyalty && leftLoyalty > 0) finalLoyalty = leftLoyalty + 1;

        int finalRiptide = Math.max(leftRiptide, rightRiptide);
        if (leftRiptide == rightRiptide && leftRiptide > 0) finalRiptide = leftRiptide + 1;

        if ((finalLoyalty > 0 && finalRiptide > 0) || (leftLoyalty > 0 && rightRiptide > 0) || (leftRiptide > 0 && rightLoyalty > 0)) {
            ItemStack result = left.clone();
            if (result.getType() == Material.TRIDENT) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null) {
                    if (finalLoyalty > 0) meta.addEnchant(Enchantment.LOYALTY, finalLoyalty, true);
                    if (finalRiptide > 0) meta.addEnchant(Enchantment.RIPTIDE, finalRiptide, true);
                    result.setItemMeta(meta);
                }
            } else {
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) result.getItemMeta();
                if (meta != null) {
                    if (finalLoyalty > 0) meta.addStoredEnchant(Enchantment.LOYALTY, finalLoyalty, true);
                    if (finalRiptide > 0) meta.addStoredEnchant(Enchantment.RIPTIDE, finalRiptide, true);
                    result.setItemMeta(meta);
                }
            }
            event.setResult(result);
        }
    }

    /**
     * SỬA LỖI CHÍNH: Ép hệ thống nhận diện hành động ném khi dùng ngoài nước hoặc tay phụ
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        ItemStack item = event.getItem();
        if (!hasBothEnchants(item)) return;

        boolean isInWater = player.isInWater() || player.getWorld().hasStorm();

        // Trường hợp 1: Cầm ở tay phụ (Offhand) -> LUÔN LÀ LOYALTY (bất kể trong hay ngoài nước)
        // Trường hợp 2: Cầm ở tay chính nhưng ở TRÊN CẠN -> PHẢI LÀ LOYALTY
        if (hand == EquipmentSlot.OFF_HAND || (hand == EquipmentSlot.HAND && !isInWater)) {
            // Đánh lừa hệ thống bằng cách tạo một thực thể Đinh ba ném đi thủ công
            // vì vanilla sẽ chặn cứng hành vi chuột phải đinh ba Riptide khi không có nước.
            
            event.setCancelled(true); // Chặn hành động vanilla (Riptide bị lỗi/không hoạt động)

            // Tính toán hướng ném dựa trên hướng nhìn của người chơi
            Location launchLoc = player.getEyeLocation();
            Vector velocity = launchLoc.getDirection().multiply(2.5); // Tốc độ bay vanilla của đinh ba

            // Tạo thực thể Đinh ba bay đi
            Trident trident = player.getWorld().spawn(launchLoc, Trident.class);
            trident.setShooter(player);
            trident.setVelocity(velocity);
            trident.setItemStack(item.clone());
            
            // Ép trạng thái nhặt của mũi tên sang trạng thái được phép nhặt (DISALLOWED / ALLOWED tùy bạn muốn)
            trident.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.ALLOWED);

            // Trừ độ bền của Đinh ba giống như vanilla khi ném (1 độ bền)
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
                if (damageable != null) {
                    damageable.setDamage(damageable.getDamage() + 1);
                    item.setItemMeta(damageable);
                    if (damageable.getDamage() >= item.getType().getMaxDurability()) {
                        item.setAmount(0);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                    }
                }
            }
            
            // Phát âm thanh tiếng ném đinh ba vanilla
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ITEM_TRIDENT_THROW, 1.0F, 1.0F);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean mainHasBoth = hasBothEnchants(mainHand);
        boolean offHasBoth = hasBothEnchants(offHand);

        // Offhand luôn là Loyalty, chặn đứng nếu Riptide cố kích hoạt ở Offhand
        if (offHasBoth && !mainHasBoth) {
            event.setCancelled(true);
            return;
        }

        // Trên cạn thì chặn Riptide
        if (mainHasBoth && !player.isInWater() && !player.getWorld().hasStorm()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        ItemStack thrownTrident = trident.getItemStack();
        if (!hasBothEnchants(thrownTrident)) return;

        // Nếu dưới nước mà tay chính cố tình ném đinh ba ra thực thể (trong khi đáng lẽ phải bay Riptide) -> Hủy thực thể ném
        if (player.isInWater() || player.getWorld().hasStorm()) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.isSimilar(thrownTrident)) {
                event.setCancelled(true);
            }
        }
    }
}
