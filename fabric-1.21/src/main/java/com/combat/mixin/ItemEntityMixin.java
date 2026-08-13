package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getStack();
    @Shadow public abstract void setStack(ItemStack stack);

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ItemStack stack = getStack();
        if (stack.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        int maxAllowed = CombatMod.getMaxAllowedCount(serverPlayer, itemId);

        if (maxAllowed <= 0) {
            ci.cancel(); // Player is already at limit, cannot pick up any!
            return;
        }

        if (stack.getCount() > maxAllowed) {
            ItemEntity entity = (ItemEntity)(Object)this;
            ItemStack pickupStack = stack.split(maxAllowed);
            if (serverPlayer.getInventory().insertStack(pickupStack)) {
                serverPlayer.sendPickup(entity, maxAllowed);
                if (stack.isEmpty()) {
                    entity.discard();
                } else {
                    setStack(stack);
                }
            } else {
                stack.increment(pickupStack.getCount());
                setStack(stack);
            }
            ci.cancel(); // Cancel standard full-stack pickup
        }
    }
}
