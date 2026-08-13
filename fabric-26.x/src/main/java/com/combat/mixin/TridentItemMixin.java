package com.combat.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.world.item.TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void onReleaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged, CallbackInfoReturnable<?> cir) {
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
            int riptide = (int) EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
            if (riptide > 0) {
                UUID uuid = player.getUUID();
                long now = System.currentTimeMillis();
                Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
                if (combatEnd != null && now < combatEnd) {
                    player.getCooldowns().addCooldown(new ItemStack(Items.TRIDENT), 1);
                }
            }
        }
    }
}
