package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PotionBrewing.class)
public class PotionBrewingMixin {

    @Inject(method = "hasRecipe", at = @At("RETURN"), cancellable = true)
    private void onHasRecipe(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        PotionBrewing manager = (PotionBrewing)(Object)this;
        ItemStack result = manager.mix(ingredient, input);
        if (result.isEmpty()) return;

        PotionContents potionContents = result.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            Optional<Holder<Potion>> potionOpt = potionContents.potion();
            if (potionOpt.isPresent()) {
                Potion potion = potionOpt.get().value();

                ResourceLocation potionId = potionOpt.get().unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
                if (potionId != null) {
                    String idStr = potionId.toString();
                    Integer limit = ConfigManager.getConfig().potionLimits.get(idStr);
                    if (limit != null && limit == -1) {
                        cir.setReturnValue(false);
                        return;
                    }
                }

                for (MobEffectInstance effect : potion.getEffects()) {
                    ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
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
                ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
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
