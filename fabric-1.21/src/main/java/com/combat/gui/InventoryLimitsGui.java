package com.combat.gui;

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

public class InventoryLimitsGui extends ChestGui {
    private final List<String> itemIds = new ArrayList<>();

    public InventoryLimitsGui(ServerPlayerEntity player) {
        super(player, "Inventory Limits Config", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clear();
        itemIds.clear();

        // Border
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(27 + i, border);
        }

        // Back Button
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(27, back);

        // Add Button
        ItemStack add = new ItemStack(Items.EMERALD);
        add.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Add New Item Limit").formatted(Formatting.GREEN));
        inventory.setStack(35, add);

        // Render current limits
        int slot = 9;
        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().itemLimits.entrySet()) {
            if (slot >= 27) break; // page limit for simplicity
            String itemId = entry.getKey();
            int limit = entry.getValue();

            ItemStack item = new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
            item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal(itemId).formatted(Formatting.GOLD));
            
            // Add lore for limit
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Current Limit: " + limit).formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to Edit/Remove").formatted(Formatting.YELLOW, Formatting.ITALIC));
            item.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(slot, item);
            itemIds.add(itemId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 35) {
            // Open Anvil input for item ID
            new AnvilInputGui(player, "Enter Item ID", "minecraft:cobblestone") {
                @Override
                protected void handleInput(String inputItemId) {
                    new AnvilInputGui(player, "Enter Limit Amount", "64") {
                        @Override
                        protected void handleInput(String inputAmount) {
                            try {
                                int amount = Integer.parseInt(inputAmount.trim());
                                ConfigManager.getConfig().itemLimits.put(inputItemId.trim(), amount);
                                ConfigManager.save();
                                player.sendMessage(Text.literal("Set limit for " + inputItemId + " to " + amount).formatted(Formatting.GREEN), false);
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                            }
                            // Reopen limits screen
                            new InventoryLimitsGui(player).open();
                        }
                    }.open();
                }
            }.open();
        } else if (slotId >= 9 && slotId < 9 + itemIds.size()) {
            String itemId = itemIds.get(slotId - 9);
            // Open Edit/Remove Confirmation Screen
            new ChestGui(player, "Edit: " + itemId, 1) {
                @Override
                protected void setupItems() {
                    // Green Stained Glass Pane (Edit Limit)
                    ItemStack edit = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                    edit.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Change Limit").formatted(Formatting.GREEN));
                    inventory.setStack(2, edit);

                    // Red Stained Glass Pane (Remove Limit)
                    ItemStack remove = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                    remove.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Delete Limit").formatted(Formatting.RED));
                    inventory.setStack(6, remove);

                    // Back Arrow
                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel").formatted(Formatting.GRAY));
                    inventory.setStack(4, backArrow);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, SlotActionType actionType) {
                    if (subSlotId == 2) {
                        // Change limit
                        new AnvilInputGui(player, "Change Limit", String.valueOf(ConfigManager.getConfig().itemLimits.get(itemId))) {
                            @Override
                            protected void handleInput(String inputAmount) {
                                try {
                                    int amount = Integer.parseInt(inputAmount.trim());
                                    ConfigManager.getConfig().itemLimits.put(itemId, amount);
                                    ConfigManager.save();
                                } catch (NumberFormatException e) {
                                    player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                                }
                                new InventoryLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        // Remove limit
                        ConfigManager.getConfig().itemLimits.remove(itemId);
                        ConfigManager.save();
                        player.sendMessage(Text.literal("Removed limit for " + itemId).formatted(Formatting.YELLOW), false);
                        new InventoryLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new InventoryLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
