package com.combat.gui;

import com.combat.CombatConfig;
import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    private static final int[] ITEM_SLOTS  = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] RESET_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    public WorldLimitsGui(ServerPlayerEntity player) {
        super(player, "World Item Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clear();

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(27 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(27, back);

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
            itemStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD));

            List<Text> lore = new ArrayList<>();
            if (!hasLimit) {
                lore.add(Text.literal("World Limit: UNLIMITED").formatted(Formatting.GRAY));
            } else {
                lore.add(Text.literal("World Limit: Max " + currentLimit + " in world").formatted(Formatting.GREEN, Formatting.BOLD));
            }
            lore.add(Text.literal(""));
            lore.add(Text.literal("Left-click to set limit").formatted(Formatting.YELLOW, Formatting.ITALIC));
            itemStack.set(net.minecraft.component.DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(lore));
            inventory.setStack(ITEM_SLOTS[i], itemStack);

            if (hasLimit) {
                ItemStack reset = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                reset.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Reset to Unlimited").formatted(Formatting.RED));
                List<Text> resetLore = new ArrayList<>();
                resetLore.add(Text.literal("Click to remove the world limit").formatted(Formatting.GRAY, Formatting.ITALIC));
                reset.set(net.minecraft.component.DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(resetLore));
                inventory.setStack(RESET_SLOTS[i], reset);
            } else {
                ItemStack empty = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                empty.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("No Limit Set").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
                inventory.setStack(RESET_SLOTS[i], empty);
            }
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
            return;
        }

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
                                player.sendMessage(Text.literal("Removed world limit for " + getItemDisplayName(itemId)).formatted(Formatting.YELLOW), false);
                            } else {
                                CombatConfig.WorldLimitedItem item = ConfigManager.getConfig().worldLimits.computeIfAbsent(itemId, k -> new CombatConfig.WorldLimitedItem());
                                item.maxCount = count;
                                player.sendMessage(Text.literal("World limit for " + getItemDisplayName(itemId) + " set to " + count).formatted(Formatting.GREEN), false);
                            }
                            ConfigManager.save();
                        } catch (NumberFormatException e) {
                            player.sendMessage(Text.literal("Invalid number!").formatted(Formatting.RED), false);
                        }
                        new WorldLimitsGui(player).open();
                    }
                }.open();
                return;
            }
        }

        for (int i = 0; i < RESET_SLOTS.length; i++) {
            if (slotId == RESET_SLOTS[i]) {
                String itemId = TARGET_ITEMS[i];
                ConfigManager.getConfig().worldLimits.remove(itemId);
                ConfigManager.save();
                player.sendMessage(Text.literal("World limit for " + getItemDisplayName(itemId) + " reset to unlimited.").formatted(Formatting.YELLOW), false);
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
