package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {
    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void enforceEnchantLimits(CallbackInfo ci) {
        if (this.levelCost.get() <= 0) {
            this.levelCost.set(1);
        }

        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack resultStack = handler.getSlot(2).getStack();
        if (resultStack.isEmpty()) return;

        ItemEnchantmentsComponent component = resultStack.getEnchantments();
        if (component.isEmpty()) return;

        Map<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>, Integer> cappedEnchants = new HashMap<>();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> entry : component.getEnchantmentEntries()) {
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            net.minecraft.util.Identifier id = enchantment.getKey().map(net.minecraft.registry.RegistryKey::getValue).orElse(null);
            if (id != null) {
                String enchantId = id.toString();
                Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);
                if (limit != null) {
                    if (limit == 0) {
                        cappedEnchants.put(enchantment, 0);
                    } else if (level > limit) {
                        cappedEnchants.put(enchantment, limit);
                    }
                }
            }
        }

        if (!cappedEnchants.isEmpty()) {
            EnchantmentHelper.apply(resultStack, builder -> {
                for (Map.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>, Integer> entry : cappedEnchants.entrySet()) {
                    if (entry.getValue() == 0) {
                        builder.remove(entry.getKey()::equals);
                    } else {
                        builder.set(entry.getKey(), entry.getValue());
                    }
                }
            });
            handler.sendContentUpdates();
        }
    }
}
