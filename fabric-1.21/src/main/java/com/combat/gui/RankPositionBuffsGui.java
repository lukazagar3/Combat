package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class RankPositionBuffsGui extends ChestGui {
    private final int position;

    public RankPositionBuffsGui(ServerPlayerEntity player, int position) {
        super(player, "Buffs for Position #" + position, 1);
        this.position = position;
    }

    @Override
    protected void setupItems() {
        inventory.clear();

        ItemStack backArrow = new ItemStack(Items.ARROW);
        backArrow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Ranks").formatted(Formatting.YELLOW));
        inventory.setStack(0, backArrow);

        int targetHealth = Math.max(2, 40 - 2 * (position - 1));
        ItemStack hpItem = new ItemStack(Items.APPLE);
        hpItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Dynamic Max Health").formatted(Formatting.RED));
        List<Text> hpLore = new ArrayList<>();
        hpLore.add(Text.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").formatted(Formatting.GRAY));
        hpLore.add(Text.literal("Automatically calculated based on rank").formatted(Formatting.DARK_GRAY));
        hpItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(hpLore));
        inventory.setStack(2, hpItem);

        List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(position, k -> new ArrayList<>());

        boolean hasSpeed = effects.contains("speed");
        ItemStack speedItem = new ItemStack(Items.SUGAR);
        speedItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Speed Effect").formatted(Formatting.AQUA));
        List<Text> speedLore = new ArrayList<>();
        speedLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(hasSpeed ? Text.literal("ACTIVE").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("INACTIVE").formatted(Formatting.RED, Formatting.BOLD)));
        speedLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        speedItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(speedLore));
        inventory.setStack(4, speedItem);

        boolean hasStrength = effects.contains("strength");
        ItemStack strengthItem = new ItemStack(Items.BLAZE_POWDER);
        strengthItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Strength Effect").formatted(Formatting.DARK_RED));
        List<Text> strengthLore = new ArrayList<>();
        strengthLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(hasStrength ? Text.literal("ACTIVE").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("INACTIVE").formatted(Formatting.RED, Formatting.BOLD)));
        strengthLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        strengthItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(strengthLore));
        inventory.setStack(6, strengthItem);

        boolean hasFireRes = effects.contains("fire_resistance");
        ItemStack fireResItem = new ItemStack(Items.MAGMA_CREAM);
        fireResItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Fire Resistance Effect").formatted(Formatting.GOLD));
        List<Text> fireResLore = new ArrayList<>();
        fireResLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(hasFireRes ? Text.literal("ACTIVE").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("INACTIVE").formatted(Formatting.RED, Formatting.BOLD)));
        fireResLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        fireResItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(fireResLore));
        inventory.setStack(8, fireResItem);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
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
        } else if (slotId == 8) {
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(position, k -> new ArrayList<>());
            if (effects.contains("fire_resistance")) {
                effects.remove("fire_resistance");
            } else {
                effects.add("fire_resistance");
            }
            ConfigManager.save();
            setupItems();
        }
    }
}
