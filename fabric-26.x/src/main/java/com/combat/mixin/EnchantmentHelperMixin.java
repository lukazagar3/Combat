package com.combat.mixin;

import com.combat.CombatMod;
import com.combat.ConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {

    @Inject(method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I", at = @At("HEAD"), cancellable = true)
    private static void onGetEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Player player && enchantment != null) {
            String enchName = enchantment.getRegisteredName().toLowerCase();
            if (enchName.contains("lunge")) {
                UUID uuid = player.getUUID();
                Long exp = CombatMod.spearLungeExpiration.get(uuid);
                if (exp != null && System.currentTimeMillis() < exp) {
                    cir.setReturnValue(0);
                }
            }
        }
    }

    @Inject(method = "doPostPiercingAttackEffects", at = @At("HEAD"), cancellable = true)
    private static void onDoPostPiercingAttackEffects(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof Player player) {
            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            Long exp = CombatMod.spearLungeExpiration.get(uuid);

            if (exp != null && now < exp) {
                // On cooldown: cancel lunge impulse & sound
                ci.cancel();
            } else {
                // Only trigger lunge cooldown if the attack was an actual LUNGE (player is sprinting)
                if (player.isSprinting()) {
                    Long combatEnd = CombatMod.combatTagExpiration.get(uuid);
                    boolean inCombat = combatEnd != null && now < combatEnd;
                    if (inCombat) {
                        Double spearSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:spear");
                        if (spearSec == null || spearSec <= 0.0) spearSec = 5.0;
                        CombatMod.pendingLungeCooldown.put(uuid, now + (long)(spearSec * 1000L));
                    }
                }
            }
        }
    }
}
