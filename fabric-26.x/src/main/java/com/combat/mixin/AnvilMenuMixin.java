package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void zeroLevelCost(CallbackInfo ci) {
        this.cost.set(0);

        AnvilMenu handler = (AnvilMenu)(Object)this;
        ItemStack resultStack = handler.getSlot(2).getItem();
        if (resultStack.isEmpty()) return;

        ItemEnchantments enchantments = resultStack.getEnchantments();
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        boolean changed = false;

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            ResourceLocation id = enchantment.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
            if (id != null) {
                String enchantId = id.toString();
                Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);
                if (limit != null && level > limit) {
                    mutable.set(enchantment, limit);
                    changed = true;
                }
            }
        }

        if (changed) {
            EnchantmentHelper.setEnchantments(resultStack, mutable.toImmutable());
            handler.broadcastChanges();
        }
    }
}
