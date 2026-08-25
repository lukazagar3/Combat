package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.util.CooldownsPlayerAccessor;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;

@Mixin(ItemCooldowns.class)
public class ItemCooldownsMixin implements CooldownsPlayerAccessor {
    private Player combat$player;

    @Override
    public void combat$setPlayer(Player player) {
        this.combat$player = player;
    }

    @Override
    public Player combat$getPlayer() {
        return this.combat$player;
    }

    @Inject(method = "addCooldown", at = @At("HEAD"), cancellable = true)
    private void overrideCooldown(ItemStack stack, int duration, CallbackInfo ci) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId.contains("spear")) return; // Do not animate cooldown on spear

        if (combat$player != null) {
            UUID uuid = combat$player.getUUID();
            Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
            if (!inCombat) return; // Cooldown overrides ONLY apply in combat!
        }

        Double customCooldown = ConfigManager.getConfig().itemCooldowns.get(itemId);
        if (customCooldown != null && customCooldown > 0.0) {
            int customTicks = (int) (customCooldown * 20);
            if (duration != customTicks) {
                ItemCooldowns manager = (ItemCooldowns) (Object) this;
                manager.addCooldown(stack, customTicks);
                ci.cancel();
            }
        }
    }
}
