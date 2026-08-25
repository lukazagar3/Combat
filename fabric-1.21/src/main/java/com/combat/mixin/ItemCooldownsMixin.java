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
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (itemId.contains("spear")) return; // Do not animate cooldown on spear

        if (combat$player != null) {
            UUID uuid = combat$player.getUuid();
            Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
            if (!inCombat) return; // Cooldown overrides ONLY apply in combat!
        }

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
