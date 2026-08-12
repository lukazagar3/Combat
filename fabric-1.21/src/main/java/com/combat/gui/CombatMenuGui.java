package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CombatMenuGui extends ChestGui {

    public CombatMenuGui(ServerPlayerEntity player) {
        super(player, "Combat Settings Menu", 3);
    }

    @Override
    protected void setupItems() {
        // Fill borders with Gray Stained Glass Pane
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(18 + i, border);
        }

        // Inventory Limits
        ItemStack limits = new ItemStack(Items.CHEST);
        limits.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Inventory Limits").formatted(Formatting.GREEN, Formatting.BOLD));
        inventory.setStack(10, limits);

        // Cooldowns
        ItemStack cooldowns = new ItemStack(Items.CLOCK);
        cooldowns.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Item Cooldowns").formatted(Formatting.AQUA, Formatting.BOLD));
        inventory.setStack(11, cooldowns);

        // Combat Log
        ItemStack combatLog = new ItemStack(Items.REDSTONE);
        combatLog.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Combat Log Settings").formatted(Formatting.RED, Formatting.BOLD));
        inventory.setStack(12, combatLog);

        // Ranked System
        ItemStack ranked = new ItemStack(Items.GOLDEN_HELMET);
        ranked.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ranked System").formatted(Formatting.GOLD, Formatting.BOLD));
        inventory.setStack(13, ranked);

        // World Limits
        ItemStack worldLimits = new ItemStack(Items.NETHER_STAR);
        worldLimits.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("World Item Limits").formatted(Formatting.YELLOW, Formatting.BOLD));
        inventory.setStack(14, worldLimits);

        // Enchant Limits
        ItemStack enchantLimits = new ItemStack(Items.ENCHANTED_BOOK);
        enchantLimits.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Enchant Level Limits").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        inventory.setStack(15, enchantLimits);

        // Potion Control (Strength Potion Icon)
        ItemStack strengthPotion = PotionContentsComponent.createStack(Items.POTION, Potions.STRENGTH);
        strengthPotion.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Potion Control System").formatted(Formatting.DARK_RED, Formatting.BOLD));
        inventory.setStack(16, strengthPotion);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 10) {
            new InventoryLimitsGui(player).open();
        } else if (slotId == 11) {
            new CooldownsGui(player).open();
        } else if (slotId == 12) {
            new CombatLogGui(player).open();
        } else if (slotId == 13) {
            new RankedGui(player).open();
        } else if (slotId == 14) {
            new WorldLimitsGui(player).open();
        } else if (slotId == 15) {
            new EnchantLimitsGui(player).open();
        } else if (slotId == 16) {
            new PotionControlGui(player).open();
        }
    }
}
