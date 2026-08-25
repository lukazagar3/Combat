package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents world-limited items from being moved into ANY container via hoppers.
 * World-limited items (e.g. mace limited to 1) must stay in player inventory or on ground.
 * They can be dropped but cannot be stored in chests, hoppers, shelves, or any container.
 */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

    /**
     * Block hopper-to-container item transfer for world-limited items.
     * This covers both pushing into chests and pulling from above containers.
     */
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void blockWorldLimitedHopperTransfer(
            Container source, Container destination, ItemStack stack, Direction direction,
            CallbackInfoReturnable<ItemStack> cir) {
        if (stack.isEmpty()) return;
        java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits =
                ConfigManager.getConfig().worldLimits;
        if (worldLimits.isEmpty()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            // Return the stack unchanged -- nothing gets moved
            cir.setReturnValue(stack);
        }
    }

    /**
     * Block hoppers from picking up world-limited item entities off the ground into containers.
     */
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void blockWorldLimitedHopperPickup(
            Container container, ItemEntity itemEntity,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits =
                ConfigManager.getConfig().worldLimits;
        if (worldLimits.isEmpty()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            // Don't let the hopper pick it up
            cir.setReturnValue(false);
        }
    }
}
