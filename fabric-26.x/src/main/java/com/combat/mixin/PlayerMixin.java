package com.combat.mixin;

import net.minecraft.world.entity.player.Player;
import com.combat.util.CooldownsPlayerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (self.getCooldowns() instanceof CooldownsPlayerAccessor accessor) {
            accessor.combat$setPlayer(self);
        }
    }
}
