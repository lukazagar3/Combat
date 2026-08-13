package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void enforceEnchantLimits(CallbackInfo ci) {
        if (this.cost.get() <= 0) {
            this.cost.set(1);
        }

        AnvilMenu handler = (AnvilMenu)(Object)this;
        ItemStack resultStack = handler.getSlot(2).getItem();
        if (resultStack.isEmpty()) return;

        ItemEnchantments enchantments = resultStack.getEnchantments();
        if (enchantments.isEmpty()) return;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        boolean changed = false;

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            Identifier id = enchantment.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            if (id != null) {
                String enchantId = id.toString();
                Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);
                if (limit != null) {
                    if (limit == 0) {
                        mutable.set(enchantment, 0);
                        changed = true;
                    } else if (level > limit) {
                        mutable.set(enchantment, limit);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            EnchantmentHelper.setEnchantments(resultStack, mutable.toImmutable());
            handler.broadcastChanges();
        }
    }
}
