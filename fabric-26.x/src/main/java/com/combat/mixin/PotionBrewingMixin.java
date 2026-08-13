package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PotionBrewing.class)
public class PotionBrewingMixin {

    @Inject(method = "hasMix", at = @At("RETURN"), cancellable = true)
    private void onHasMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        PotionBrewing manager = (PotionBrewing)(Object)this;
        ItemStack result = manager.mix(ingredient, input);
        if (result.isEmpty()) return;

        PotionContents potionContents = result.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            Optional<Holder<Potion>> potionOpt = potionContents.potion();
            if (potionOpt.isPresent()) {
                Potion potion = potionOpt.get().value();

                net.minecraft.resources.Identifier potionId = potionOpt.get().unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
                if (potionId != null) {
                    String idStr = potionId.toString();
                    Integer limit = ConfigManager.getConfig().potionLimits.get(idStr);
                    if (limit != null && limit == -1) {
                        cir.setReturnValue(false);
                        return;
                    }
                }

                for (MobEffectInstance effect : potion.getEffects()) {
                    net.minecraft.resources.Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                    if (effectId != null) {
                        String effectIdStr = effectId.toString();
                        Integer limit = ConfigManager.getConfig().potionLimits.get(effectIdStr);
                        if (limit != null) {
                            if (limit == -1 || (effect.getAmplifier() + 1) > limit) {
                                cir.setReturnValue(false);
                                return;
                            }
                        }
                    }
                }
            }

            for (MobEffectInstance effect : potionContents.customEffects()) {
                net.minecraft.resources.Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                if (effectId != null) {
                    String effectIdStr = effectId.toString();
                    Integer limit = ConfigManager.getConfig().potionLimits.get(effectIdStr);
                    if (limit != null) {
                        if (limit == -1 || (effect.getAmplifier() + 1) > limit) {
                            cir.setReturnValue(false);
                            return;
                        }
                    }
                }
            }
        }
    }
}
