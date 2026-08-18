package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.util.CooldownsPlayerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

@Mixin(ItemCooldownManager.class)
public class ItemCooldownsMixin implements CooldownsPlayerAccessor {
    private PlayerEntity combat$player;

    @Override
    public void combat$setPlayer(PlayerEntity player) {
        this.combat$player = player;
    }

    @Override
    public PlayerEntity combat$getPlayer() {
        return this.combat$player;
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void overrideCooldown(ItemStack stack, int duration, CallbackInfo ci) {
        if (this.combat$player instanceof ServerPlayerEntity serverPlayer) {
            UUID uuid = serverPlayer.getUuid();
            long now = System.currentTimeMillis();
            Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && now < combatEnd;
            if (!inCombat) {
                return;
            }
        } else {
            return;
        }

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        Double customCooldown = ConfigManager.getConfig().itemCooldowns.get(itemId);

        if (customCooldown != null && customCooldown > 0.0) {
            int customTicks = (int) (customCooldown * 20);
            if (duration != customTicks) {
                ItemCooldownManager manager = (ItemCooldownManager) (Object) this;
                manager.set(stack, customTicks);
                ci.cancel();
            }
        }
    }
}
