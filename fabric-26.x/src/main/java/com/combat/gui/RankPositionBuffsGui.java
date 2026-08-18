package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class RankPositionBuffsGui extends ChestGui {
    private final int position;

    public RankPositionBuffsGui(ServerPlayer player, int position) {
        super(player, "Buffs for Position #" + position, 1);
        this.position = position;
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();

        ItemStack backArrow = new ItemStack(Items.ARROW);
        backArrow.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Ranks").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(0, backArrow);

        int targetHealth = Math.max(2, 40 - 2 * (position - 1));
        ItemStack hpItem = new ItemStack(Items.APPLE);
        hpItem.set(DataComponents.CUSTOM_NAME, Component.literal("Dynamic Max Health").withStyle(ChatFormatting.RED));
        List<Component> hpLore = new ArrayList<>();
        hpLore.add(Component.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").withStyle(ChatFormatting.GRAY));
        hpLore.add(Component.literal("Automatically calculated based on rank").withStyle(ChatFormatting.DARK_GRAY));
        hpItem.set(DataComponents.LORE, new ItemLore(hpLore));
        inventory.setItem(2, hpItem);

        List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(position, k -> new ArrayList<>());

        boolean hasSpeed = effects.contains("speed");
        ItemStack speedItem = new ItemStack(Items.SUGAR);
        speedItem.set(DataComponents.CUSTOM_NAME, Component.literal("Speed Effect").withStyle(ChatFormatting.AQUA));
        List<Component> speedLore = new ArrayList<>();
        speedLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
            .append(hasSpeed ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        speedLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        speedItem.set(DataComponents.LORE, new ItemLore(speedLore));
        inventory.setItem(4, speedItem);

        boolean hasStrength = effects.contains("strength");
        ItemStack strengthItem = new ItemStack(Items.BLAZE_POWDER);
        strengthItem.set(DataComponents.CUSTOM_NAME, Component.literal("Strength Effect").withStyle(ChatFormatting.DARK_RED));
        List<Component> strengthLore = new ArrayList<>();
        strengthLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
            .append(hasStrength ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        strengthLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        strengthItem.set(DataComponents.LORE, new ItemLore(strengthLore));
        inventory.setItem(6, strengthItem);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId == 0) {
            new RankedGui(player).open();
        } else if (slotId == 4) {
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(position, k -> new ArrayList<>());
            if (effects.contains("speed")) {
                effects.remove("speed");
            } else {
                effects.add("speed");
            }
            ConfigManager.save();
            setupItems();
        } else if (slotId == 6) {
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(position, k -> new ArrayList<>());
            if (effects.contains("strength")) {
                effects.remove("strength");
            } else {
                effects.add("strength");
            }
            ConfigManager.save();
            setupItems();
        }
    }
}
