package com.combat.mixin;

import net.minecraft.entity.player.PlayerEntity;
import com.combat.util.CooldownsPlayerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getItemCooldownManager() instanceof CooldownsPlayerAccessor accessor) {
            accessor.combat$setPlayer(self);
        }
    }
}
