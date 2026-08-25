package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents world-limited items from being moved into ANY container via hoppers.
 * World-limited items (e.g. mace limited to 1) must stay in player inventory or on ground.
 */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

    /**
     * Block hopper-to-container item transfer for world-limited items.
     */
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void blockWorldLimitedHopperTransfer(
            Inventory from, Inventory to, ItemStack stack, Direction direction,
            CallbackInfoReturnable<ItemStack> cir) {
        if (stack.isEmpty()) return;
        java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits =
                ConfigManager.getConfig().worldLimits;
        if (worldLimits.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            // Return the stack unchanged -- nothing gets moved
            cir.setReturnValue(stack);
        }
    }

    /**
     * Block hoppers from picking up world-limited item entities off the ground.
     */
    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void blockWorldLimitedHopperPickup(
            Inventory inventory, ItemEntity itemEntity,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = itemEntity.getStack();
        if (stack.isEmpty()) return;
        java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits =
                ConfigManager.getConfig().worldLimits;
        if (worldLimits.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            cir.setReturnValue(false);
        }
    }
}
