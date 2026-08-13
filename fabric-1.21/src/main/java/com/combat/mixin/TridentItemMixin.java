package com.combat.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(net.minecraft.item.TridentItem.class)
public class TridentItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<?> cir) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            var registry = world.getRegistryManager().getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT).orElse(null);
            var entry = registry != null ? registry.getEntry(net.minecraft.util.Identifier.of("minecraft", "riptide")).orElse(null) : null;
            int riptide = entry != null ? EnchantmentHelper.getLevel(entry, stack) : 0;
            if (riptide > 0) {
                UUID uuid = player.getUuid();
                long now = System.currentTimeMillis();
                Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
                if (combatEnd != null && now < combatEnd) {
                    player.getItemCooldownManager().set(new ItemStack(Items.TRIDENT), 1);
                }
            }
        }
    }
}
