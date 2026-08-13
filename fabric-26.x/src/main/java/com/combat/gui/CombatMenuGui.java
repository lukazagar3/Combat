package com.combat.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;

public class CombatMenuGui extends ChestGui {

    public CombatMenuGui(ServerPlayer player) {
        super(player, "Combat Settings Menu", 3);
    }

    @Override
    protected void setupItems() {
        ItemStack border = new ItemStack(Items.GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(18 + i, border);
        }

        ItemStack limits = new ItemStack(Items.CHEST);
        limits.set(DataComponents.CUSTOM_NAME, Component.literal("Inventory Limits").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        inventory.setItem(10, limits);

        ItemStack cooldowns = new ItemStack(Items.CLOCK);
        cooldowns.set(DataComponents.CUSTOM_NAME, Component.literal("Item Cooldowns").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        inventory.setItem(11, cooldowns);

        ItemStack combatLog = new ItemStack(Items.REDSTONE);
        combatLog.set(DataComponents.CUSTOM_NAME, Component.literal("Combat Log Settings").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        inventory.setItem(12, combatLog);

        ItemStack ranked = new ItemStack(Items.NETHER_STAR);
        ranked.set(DataComponents.CUSTOM_NAME, Component.literal("Ranked System").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        inventory.setItem(13, ranked);

        ItemStack worldLimits = new ItemStack(Items.MACE);
        worldLimits.set(DataComponents.CUSTOM_NAME, Component.literal("World Item Limits").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        inventory.setItem(14, worldLimits);

        ItemStack enchantLimits = new ItemStack(Items.ENCHANTED_BOOK);
        enchantLimits.set(DataComponents.CUSTOM_NAME, Component.literal("Enchant Level Limits").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        inventory.setItem(15, enchantLimits);

        ItemStack strengthPotion = new ItemStack(Items.GLASS_BOTTLE);
        strengthPotion.set(DataComponents.CUSTOM_NAME, Component.literal("Potion Control System").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        inventory.setItem(16, strengthPotion);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
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
            new EnchantLimitsGui(player, 0, "").open();
        } else if (slotId == 16) {
            new PotionControlGui(player).open();
        }
    }
}
