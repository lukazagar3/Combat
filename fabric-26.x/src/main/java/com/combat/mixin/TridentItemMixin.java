package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.CombatMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.world.item.TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void onReleaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged, CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
            int riptide = (int) EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            Long combatEnd = CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && now < combatEnd;

            if (riptide > 0 && inCombat) {
                Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:trident");
                if (cooldownSec != null && cooldownSec > 0.0) {
                    Long expiration = CombatMod.tridentRiptideExpiration.get(uuid);
                    if (expiration != null && now < expiration) {
                        double remaining = (expiration - now) / 1000.0;
                        player.sendSystemMessage(Component.literal(String.format("Trident Riptide is on cooldown for %.1fs!", remaining)).withStyle(ChatFormatting.RED));
                        player.getCooldowns().addCooldown(stack, (int)(remaining * 20));
                        cir.setReturnValue(false);
                        return;
                    }

                    // Apply Riptide cooldown ONLY in combat
                    CombatMod.tridentRiptideExpiration.put(uuid, now + (long)(cooldownSec * 1000L));
                    player.getCooldowns().addCooldown(stack, (int)(cooldownSec * 20));
                }
            }
        }
    }
}
