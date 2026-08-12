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
import java.util.Map;

public class EnchantLimitsGui extends ChestGui {
    private final List<String> enchantIds = new ArrayList<>();

    public EnchantLimitsGui(ServerPlayerEntity player) {
        super(player, "Enchant Level Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clear();
        enchantIds.clear();

        // Border
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(27 + i, border);
        }

        // Back Arrow
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(27, back);

        // Add Button
        ItemStack add = new ItemStack(Items.EMERALD);
        add.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Add Enchant Limit").formatted(Formatting.GREEN));
        inventory.setStack(35, add);

        // Render current limits
        int slot = 9;
        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().enchantLimits.entrySet()) {
            if (slot >= 27) break;
            String enchantId = entry.getKey();
            int maxLevel = entry.getValue();

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal(enchantId).formatted(Formatting.GOLD));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Maximum Level Allowed: " + maxLevel).formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to Configure").formatted(Formatting.YELLOW, Formatting.ITALIC));
            book.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(slot, book);
            enchantIds.add(enchantId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 35) {
            // Add new enchant limit
            new AnvilInputGui(player, "Enter Enchant ID", "minecraft:protection") {
                @Override
                protected void handleInput(String inputEnchantId) {
                    new AnvilInputGui(player, "Enter Max Allowed Level", "3") {
                        @Override
                        protected void handleInput(String inputLevel) {
                            try {
                                int level = Integer.parseInt(inputLevel.trim());
                                if (level < 0) {
                                    player.sendMessage(Text.literal("Level cannot be negative!").formatted(Formatting.RED), false);
                                } else {
                                    ConfigManager.getConfig().enchantLimits.put(inputEnchantId.trim(), level);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid level!").formatted(Formatting.RED), false);
                            }
                            new EnchantLimitsGui(player).open();
                        }
                    }.open();
                }
            }.open();
        } else if (slotId >= 9 && slotId < 9 + enchantIds.size()) {
            String enchantId = enchantIds.get(slotId - 9);
            // Open submenu
            new ChestGui(player, "Configure: " + enchantId, 1) {
                @Override
                protected void setupItems() {
                    // Change limit (Green glass pane)
                    ItemStack edit = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                    edit.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Change Max Level").formatted(Formatting.GREEN));
                    inventory.setStack(2, edit);

                    // Remove limit (Red glass pane)
                    ItemStack remove = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                    remove.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Remove Enchant Limit").formatted(Formatting.RED));
                    inventory.setStack(6, remove);

                    // Cancel (Arrow)
                    ItemStack cancel = new ItemStack(Items.ARROW);
                    cancel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel").formatted(Formatting.GRAY));
                    inventory.setStack(4, cancel);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, SlotActionType actionType) {
                    if (subSlotId == 2) {
                        int currentLevel = ConfigManager.getConfig().enchantLimits.getOrDefault(enchantId, 1);
                        new AnvilInputGui(player, "Set Max Level", String.valueOf(currentLevel)) {
                            @Override
                            protected void handleInput(String inputLevel) {
                                try {
                                    int level = Integer.parseInt(inputLevel.trim());
                                    if (level < 0) {
                                        player.sendMessage(Text.literal("Level cannot be negative!").formatted(Formatting.RED), false);
                                    } else {
                                        ConfigManager.getConfig().enchantLimits.put(enchantId, level);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendMessage(Text.literal("Invalid level!").formatted(Formatting.RED), false);
                                }
                                new EnchantLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        ConfigManager.getConfig().enchantLimits.remove(enchantId);
                        ConfigManager.save();
                        player.sendMessage(Text.literal("Removed enchant limit for " + enchantId).formatted(Formatting.YELLOW), false);
                        new EnchantLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new EnchantLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
