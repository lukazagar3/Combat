package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldownManager.class)
public class ItemCooldownsMixin {

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void overrideCooldown(ItemStack stack, int duration, CallbackInfo ci) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if ("minecraft:ender_pearl".equals(itemId) || "minecraft:mace".equals(itemId) || 
            "minecraft:trident".equals(itemId) || "minecraft:spear".equals(itemId)) {
            
            Double customCooldown = ConfigManager.getConfig().itemCooldowns.get(itemId);
            if (customCooldown != null && customCooldown > 0.0) {
                int customTicks = (int) (customCooldown * 20);
                if (duration != customTicks) {
                    ItemCooldownManager manager = (ItemCooldownManager)(Object)this;
                    manager.set(stack, customTicks);
                    ci.cancel();
                }
            }
        }
    }
}
