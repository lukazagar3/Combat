package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(BrewingRecipeRegistry.class)
public abstract class BrewingRecipeRegistryMixin {

    @Shadow
    public abstract ItemStack craft(ItemStack ingredient, ItemStack input);

    @Inject(method = "hasRecipe", at = @At("RETURN"), cancellable = true)
    private void onHasRecipe(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        // Simulate brewing to get the output item stack
        ItemStack result = this.craft(ingredient, input);
        if (result.isEmpty()) return;

        // Check if the result potion exceeds any configured limits
        PotionContentsComponent potionContents = result.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null) {
            Optional<RegistryEntry<Potion>> potionOpt = potionContents.potion();
            if (potionOpt.isPresent()) {
                Potion potion = potionOpt.get().value();
                
                // 1. Check if the Potion ID itself is disabled completely
                Identifier potionId = potionOpt.get().getKey().map(net.minecraft.registry.RegistryKey::getValue).orElse(null);
                if (potionId != null) {
                    String idStr = potionId.toString();
                    Integer limit = ConfigManager.getConfig().potionLimits.get(idStr);
                    if (limit != null && limit == -1) {
                        cir.setReturnValue(false); // Brewing disabled completely
                        return;
                    }
                }

                // 2. Check individual effects and their levels
                for (StatusEffectInstance effect : potion.getEffects()) {
                    Identifier effectId = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
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

            // Check custom effects (if any)
            for (StatusEffectInstance effect : potionContents.customEffects()) {
                Identifier effectId = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
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
