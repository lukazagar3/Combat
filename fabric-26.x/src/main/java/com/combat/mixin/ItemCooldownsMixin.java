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
import net.minecraft.server.level.ServerPlayer;
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
        if (this.combat$player instanceof ServerPlayer serverPlayer) {
            UUID uuid = serverPlayer.getUUID();
            long now = System.currentTimeMillis();
            Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && now < combatEnd;
            if (!inCombat) {
                return;
            }
        } else {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
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
