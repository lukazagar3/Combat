package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void sanitizeEnchantmentsOnEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
            ItemStack enchantedItem = menu.getSlot(0).getItem();
            if (enchantedItem != null && !enchantedItem.isEmpty()) {
                if (CombatMod.sanitizeItemEnchantments(enchantedItem)) {
                    menu.broadcastChanges();
                }
            }
        }
    }
}
