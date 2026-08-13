package com.combat.gui;

import com.combat.CombatConfig;
import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class WorldLimitsGui extends ChestGui {
    private static final String[] TARGET_ITEMS = {
        "minecraft:netherite_helmet",
        "minecraft:netherite_chestplate",
        "minecraft:netherite_leggings",
        "minecraft:netherite_boots",
        "minecraft:mace",
        "minecraft:netherite_sword",
        "minecraft:netherite_axe"
    };

    // Row 1 (slots 9–17): items at columns 1–7 → slots 10–16
    private static final int[] ITEM_SLOTS  = {10, 11, 12, 13, 14, 15, 16};
    // Row 2 (slots 18–26): reset buttons directly below each item
    private static final int[] RESET_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    public WorldLimitsGui(ServerPlayer player) {
        super(player, "World Item Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();

        // Top and bottom border rows
        ItemStack border = new ItemStack(Items.GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(27 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Main Menu").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(27, back);

        for (int i = 0; i < TARGET_ITEMS.length; i++) {
            String itemId = TARGET_ITEMS[i];
            CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
            int currentLimit = wli != null ? wli.maxCount : -1;
            boolean hasLimit = currentLimit > 0;

            ItemStack itemStack = switch (i) {
                case 0 -> new ItemStack(Items.NETHERITE_HELMET);
                case 1 -> new ItemStack(Items.NETHERITE_CHESTPLATE);
                case 2 -> new ItemStack(Items.NETHERITE_LEGGINGS);
                case 3 -> new ItemStack(Items.NETHERITE_BOOTS);
                case 4 -> new ItemStack(Items.MACE);
                case 5 -> new ItemStack(Items.NETHERITE_SWORD);
                default -> new ItemStack(Items.NETHERITE_AXE);
            };

            String name = getItemDisplayName(itemId);
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<Component> lore = new ArrayList<>();
            if (!hasLimit) {
                lore.add(Component.literal("World Limit: UNLIMITED").withStyle(ChatFormatting.GRAY));
            } else {
                lore.add(Component.literal("World Limit: Max " + currentLimit + " in world").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            }
            lore.add(Component.literal(""));
            lore.add(Component.literal("Left-click to set limit").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            itemStack.set(DataComponents.LORE, new ItemLore(lore));
            inventory.setItem(ITEM_SLOTS[i], itemStack);

            // Reset button below each item
            if (hasLimit) {
                net.minecraft.world.item.Item redPane = BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:red_stained_glass_pane")).map(r -> r.value()).orElse(Items.GLASS_PANE);
                ItemStack reset = new ItemStack(redPane);
                reset.set(DataComponents.CUSTOM_NAME, Component.literal("Reset to Unlimited").withStyle(ChatFormatting.RED));
                List<Component> resetLore = new ArrayList<>();
                resetLore.add(Component.literal("Click to remove the world limit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                reset.set(DataComponents.LORE, new ItemLore(resetLore));
                inventory.setItem(RESET_SLOTS[i], reset);
            } else {
                net.minecraft.world.item.Item limePane = BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:lime_stained_glass_pane")).map(r -> r.value()).orElse(Items.GLASS_PANE);
                ItemStack empty = new ItemStack(limePane);
                empty.set(DataComponents.CUSTOM_NAME, Component.literal("No Limit Set").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                inventory.setItem(RESET_SLOTS[i], empty);
            }
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
            return;
        }

        // Left-click on item → open anvil input to set limit
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (slotId == ITEM_SLOTS[i]) {
                String itemId = TARGET_ITEMS[i];
                CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
                int currentLimit = wli != null ? wli.maxCount : 1;

                new AnvilInputGui(player, "Set World Limit", String.valueOf(currentLimit)) {
                    @Override
                    protected void handleInput(String inputStr) {
                        try {
                            int count = Integer.parseInt(inputStr.trim());
                            if (count <= 0) {
                                ConfigManager.getConfig().worldLimits.remove(itemId);
                                player.sendSystemMessage(Component.literal("Removed world limit for " + getItemDisplayName(itemId)).withStyle(ChatFormatting.YELLOW));
                            } else {
                                CombatConfig.WorldLimitedItem item = ConfigManager.getConfig().worldLimits.computeIfAbsent(itemId, k -> new CombatConfig.WorldLimitedItem());
                                item.maxCount = count;
                                player.sendSystemMessage(Component.literal("World limit for " + getItemDisplayName(itemId) + " set to " + count).withStyle(ChatFormatting.GREEN));
                            }
                            ConfigManager.save();
                        } catch (NumberFormatException e) {
                            player.sendSystemMessage(Component.literal("Invalid number!").withStyle(ChatFormatting.RED));
                        }
                        new WorldLimitsGui(player).open();
                    }
                }.open();
                return;
            }
        }

        // Click on reset button → remove the limit
        for (int i = 0; i < RESET_SLOTS.length; i++) {
            if (slotId == RESET_SLOTS[i]) {
                String itemId = TARGET_ITEMS[i];
                ConfigManager.getConfig().worldLimits.remove(itemId);
                ConfigManager.save();
                player.sendSystemMessage(Component.literal("World limit for " + getItemDisplayName(itemId) + " reset to unlimited.").withStyle(ChatFormatting.YELLOW));
                new WorldLimitsGui(player).open();
                return;
            }
        }
    }

    private String getItemDisplayName(String itemId) {
        return switch (itemId) {
            case "minecraft:netherite_helmet"     -> "Netherite Helmet";
            case "minecraft:netherite_chestplate" -> "Netherite Chestplate";
            case "minecraft:netherite_leggings"   -> "Netherite Leggings";
            case "minecraft:netherite_boots"      -> "Netherite Boots";
            case "minecraft:mace"                 -> "Mace";
            case "minecraft:netherite_sword"      -> "Netherite Sword";
            case "minecraft:netherite_axe"        -> "Netherite Axe";
            default                               -> itemId;
        };
    }
}
