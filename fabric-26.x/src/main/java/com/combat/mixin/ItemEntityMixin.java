package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow public abstract ItemStack getItem();
    @Shadow public abstract void setItem(ItemStack stack);

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack stack = getItem();
        if (stack.isEmpty()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int maxAllowed = CombatMod.getMaxAllowedCount(serverPlayer, itemId);

        if (maxAllowed <= 0) {
            ci.cancel(); // Player is already at limit, cannot pick up any!
            return;
        }

        if (stack.getCount() > maxAllowed) {
            ItemEntity entity = (ItemEntity)(Object)this;
            ItemStack pickupStack = stack.split(maxAllowed);
            if (serverPlayer.getInventory().add(pickupStack)) {
                serverPlayer.take(entity, maxAllowed);
                if (stack.isEmpty()) {
                    entity.discard();
                } else {
                    setItem(stack);
                }
            } else {
                stack.grow(pickupStack.getCount());
                setItem(stack);
            }
            ci.cancel(); // Cancel standard full-stack pickup
        }
    }
}
