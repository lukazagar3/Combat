package com.combat.gui;

import com.combat.CombatConfig;
import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorldLimitsGui extends ChestGui {
    private final List<String> worldLimitItemIds = new ArrayList<>();

    public WorldLimitsGui(ServerPlayerEntity player) {
        super(player, "World Item Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clear();
        worldLimitItemIds.clear();

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
        add.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Add World Limited Item").formatted(Formatting.GREEN));
        inventory.setStack(35, add);

        // Render current world-limited items
        int slot = 9;
        for (Map.Entry<String, CombatConfig.WorldLimitedItem> entry : ConfigManager.getConfig().worldLimits.entrySet()) {
            if (slot >= 27) break;
            String itemId = entry.getKey();
            CombatConfig.WorldLimitedItem wli = entry.getValue();

            ItemStack itemStack;
            try {
                itemStack = new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
            } catch (Exception e) {
                itemStack = new ItemStack(Items.BARRIER);
            }
            itemStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal(itemId).formatted(Formatting.GOLD));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Global Server Limit: " + wli.maxCount).formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to Configure").formatted(Formatting.YELLOW, Formatting.ITALIC));
            itemStack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(slot, itemStack);
            worldLimitItemIds.add(itemId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 35) {
            // Add new world limited item
            new AnvilInputGui(player, "Enter Item ID", "minecraft:mace") {
                @Override
                protected void handleInput(String inputItemId) {
                    new AnvilInputGui(player, "Enter Max Global Count", "1") {
                        @Override
                        protected void handleInput(String inputCount) {
                            try {
                                int count = Integer.parseInt(inputCount.trim());
                                if (count <= 0) {
                                    player.sendMessage(Text.literal("Limit must be positive!").formatted(Formatting.RED), false);
                                } else {
                                    CombatConfig.WorldLimitedItem wli = new CombatConfig.WorldLimitedItem();
                                    wli.maxCount = count;
                                    ConfigManager.getConfig().worldLimits.put(inputItemId.trim(), wli);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid count!").formatted(Formatting.RED), false);
                            }
                            new WorldLimitsGui(player).open();
                        }
                    }.open();
                }
            }.open();
        } else if (slotId >= 9 && slotId < 9 + worldLimitItemIds.size()) {
            String itemId = worldLimitItemIds.get(slotId - 9);
            // Open submenu
            new ChestGui(player, "Configure: " + itemId, 1) {
                @Override
                protected void setupItems() {
                    // Change limit (Green glass pane)
                    ItemStack edit = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                    edit.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Change Global Count").formatted(Formatting.GREEN));
                    inventory.setStack(2, edit);

                    // Remove limit (Red glass pane)
                    ItemStack remove = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                    remove.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Remove World Limit").formatted(Formatting.RED));
                    inventory.setStack(6, remove);

                    // Cancel (Arrow)
                    ItemStack cancel = new ItemStack(Items.ARROW);
                    cancel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel").formatted(Formatting.GRAY));
                    inventory.setStack(4, cancel);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, SlotActionType actionType) {
                    if (subSlotId == 2) {
                        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
                        int currentCount = wli != null ? wli.maxCount : 1;
                        new AnvilInputGui(player, "Set Max Global Count", String.valueOf(currentCount)) {
                            @Override
                            protected void handleInput(String inputCount) {
                                try {
                                    int count = Integer.parseInt(inputCount.trim());
                                    if (count <= 0) {
                                        player.sendMessage(Text.literal("Limit must be positive!").formatted(Formatting.RED), false);
                                    } else {
                                        CombatConfig.WorldLimitedItem item = ConfigManager.getConfig().worldLimits.computeIfAbsent(itemId, k -> new CombatConfig.WorldLimitedItem());
                                        item.maxCount = count;
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendMessage(Text.literal("Invalid count!").formatted(Formatting.RED), false);
                                }
                                new WorldLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        ConfigManager.getConfig().worldLimits.remove(itemId);
                        ConfigManager.save();
                        player.sendMessage(Text.literal("Removed world limit for " + itemId).formatted(Formatting.YELLOW), false);
                        new WorldLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new WorldLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
