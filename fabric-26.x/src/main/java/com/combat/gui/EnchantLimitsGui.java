package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.LoreComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnchantLimitsGui extends ChestGui {
    private final List<String> enchantIds = new ArrayList<>();

    public EnchantLimitsGui(ServerPlayer player) {
        super(player, "Enchant Level Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        enchantIds.clear();

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(27 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Main Menu").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(27, back);

        ItemStack add = new ItemStack(Items.EMERALD);
        add.set(DataComponents.CUSTOM_NAME, Component.literal("Add Enchant Limit").withStyle(ChatFormatting.GREEN));
        inventory.setItem(35, add);

        int slot = 9;
        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().enchantLimits.entrySet()) {
            if (slot >= 27) break;
            String enchantId = entry.getKey();
            int maxLevel = entry.getValue();

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.CUSTOM_NAME, Component.literal(enchantId).withStyle(ChatFormatting.GOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Maximum Level Allowed: " + maxLevel).withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Configure").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            book.set(DataComponents.LORE, new LoreComponent(loreLines));

            inventory.setItem(slot, book);
            enchantIds.add(enchantId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ClickType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 35) {
            new AnvilInputGui(player, "Enter Enchant ID", "minecraft:protection") {
                @Override
                protected void handleInput(String inputEnchantId) {
                    new AnvilInputGui(player, "Enter Max Allowed Level", "3") {
                        @Override
                        protected void handleInput(String inputLevel) {
                            try {
                                int level = Integer.parseInt(inputLevel.trim());
                                if (level < 0) {
                                    player.sendSystemMessage(Component.literal("Level cannot be negative!").withStyle(ChatFormatting.RED));
                                } else {
                                    ConfigManager.getConfig().enchantLimits.put(inputEnchantId.trim(), level);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Invalid level!").withStyle(ChatFormatting.RED));
                            }
                            new EnchantLimitsGui(player).open();
                        }
                    }.open();
                }
            }.open();
        } else if (slotId >= 9 && slotId < 9 + enchantIds.size()) {
            String enchantId = enchantIds.get(slotId - 9);
            new ChestGui(player, "Configure: " + enchantId, 1) {
                @Override
                protected void setupItems() {
                    ItemStack edit = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                    edit.set(DataComponents.CUSTOM_NAME, Component.literal("Change Max Level").withStyle(ChatFormatting.GREEN));
                    inventory.setItem(2, edit);

                    ItemStack remove = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                    remove.set(DataComponents.CUSTOM_NAME, Component.literal("Remove Enchant Limit").withStyle(ChatFormatting.RED));
                    inventory.setItem(6, remove);

                    ItemStack cancel = new ItemStack(Items.ARROW);
                    cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(ChatFormatting.GRAY));
                    inventory.setItem(4, cancel);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, ClickType actionType) {
                    if (subSlotId == 2) {
                        int currentLevel = ConfigManager.getConfig().enchantLimits.getOrDefault(enchantId, 1);
                        new AnvilInputGui(player, "Set Max Level", String.valueOf(currentLevel)) {
                            @Override
                            protected void handleInput(String inputLevel) {
                                try {
                                    int level = Integer.parseInt(inputLevel.trim());
                                    if (level < 0) {
                                        player.sendSystemMessage(Component.literal("Level cannot be negative!").withStyle(ChatFormatting.RED));
                                    } else {
                                        ConfigManager.getConfig().enchantLimits.put(enchantId, level);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("Invalid level!").withStyle(ChatFormatting.RED));
                                }
                                new EnchantLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        ConfigManager.getConfig().enchantLimits.remove(enchantId);
                        ConfigManager.save();
                        player.sendSystemMessage(Component.literal("Removed enchant limit for " + enchantId).withStyle(ChatFormatting.YELLOW));
                        new EnchantLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new EnchantLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
