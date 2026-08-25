package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreenHandler.class)
public class EnchantmentScreenHandlerMixin {

    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void sanitizeEnchantmentsOnEnchant(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            EnchantmentScreenHandler handler = (EnchantmentScreenHandler) (Object) this;
            ItemStack enchantedItem = handler.getSlot(0).getStack();
            if (enchantedItem != null && !enchantedItem.isEmpty()) {
                if (CombatMod.sanitizeItemEnchantments(enchantedItem)) {
                    handler.sendContentUpdates();
                }
            }
        }
    }
}
