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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(net.minecraft.world.item.TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void onReleaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged, CallbackInfo ci) {
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
            int riptide = (int) EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
            if (riptide > 0) {
                Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:trident");
                if (cooldownSec != null && cooldownSec > 0.0) {
                    UUID uuid = player.getUUID();
                    long now = System.currentTimeMillis();
                    Long expiration = CombatMod.tridentRiptideExpiration.get(uuid);
                    if (expiration != null && now < expiration) {
                        double remaining = (expiration - now) / 1000.0;
                        player.sendSystemMessage(Component.literal(String.format("Trident Riptide is on cooldown for %.1fs!", remaining)).withStyle(ChatFormatting.RED));
                        player.getCooldowns().addCooldown(stack, (int)(remaining * 20));
                        ci.cancel();
                        return;
                    }

                    // Apply Riptide cooldown
                    CombatMod.tridentRiptideExpiration.put(uuid, now + (long)(cooldownSec * 1000L));
                    player.getCooldowns().addCooldown(stack, (int)(cooldownSec * 20));
                }
            }
        }
    }
}
