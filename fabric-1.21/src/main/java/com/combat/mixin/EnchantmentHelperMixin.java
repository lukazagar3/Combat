package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {

    @Inject(method = "getEquipmentLevel", at = @At("HEAD"), cancellable = true)
    private static void onGetEquipmentLevel(RegistryEntry<Enchantment> enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof PlayerEntity player && enchantment != null) {
            String enchName = enchantment.getIdAsString().toLowerCase();
            if (enchName.contains("lunge")) {
                UUID uuid = player.getUuid();
                Long exp = CombatMod.spearLungeExpiration.get(uuid);
                if (exp != null && System.currentTimeMillis() < exp) {
                    cir.setReturnValue(0);
                }
            }
        }
    }
}
