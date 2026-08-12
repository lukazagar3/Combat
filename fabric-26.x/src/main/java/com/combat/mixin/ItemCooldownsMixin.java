package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldowns.class)
public class ItemCooldownsMixin {

    @Inject(method = "addCooldown", at = @At("HEAD"), cancellable = true)
    private void overrideCooldown(ItemStack stack, int duration, CallbackInfo ci) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if ("minecraft:ender_pearl".equals(itemId) || "minecraft:mace".equals(itemId) || 
            "minecraft:trident".equals(itemId) || "minecraft:spear".equals(itemId)) {
            
            Double customCooldown = ConfigManager.getConfig().itemCooldowns.get(itemId);
            if (customCooldown != null && customCooldown > 0.0) {
                int customTicks = (int) (customCooldown * 20);
                if (duration != customTicks) {
                    ItemCooldowns manager = (ItemCooldowns)(Object)this;
                    manager.addCooldown(stack, customTicks);
                    ci.cancel();
                }
            }
        }
    }
}
