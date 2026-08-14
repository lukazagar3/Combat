package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.CombatMod;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.item.TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            var registry = world.getRegistryManager().getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT).orElse(null);
            var entry = registry != null ? registry.getEntry(net.minecraft.util.Identifier.of("minecraft", "riptide")).orElse(null) : null;
            int riptide = entry != null ? EnchantmentHelper.getLevel(entry, stack) : 0;
            
            UUID uuid = player.getUuid();
            long now = System.currentTimeMillis();
            Long combatEnd = CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && now < combatEnd;

            if (riptide > 0 && inCombat) {
                Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:trident");
                if (cooldownSec != null && cooldownSec > 0.0) {
                    Long expiration = CombatMod.tridentRiptideExpiration.get(uuid);
                    if (expiration != null && now < expiration) {
                        double remaining = (expiration - now) / 1000.0;
                        player.sendMessage(Text.literal(String.format("Trident Riptide is on cooldown for %.1fs!", remaining)).formatted(Formatting.RED), false);
                        player.getItemCooldownManager().set(stack, (int)(remaining * 20));
                        cir.setReturnValue(false);
                        return;
                    }

                    // Apply Riptide cooldown ONLY in combat
                    CombatMod.tridentRiptideExpiration.put(uuid, now + (long)(cooldownSec * 1000L));
                    player.getItemCooldownManager().set(stack, (int)(cooldownSec * 20));
                }
            }
        }
    }
}
