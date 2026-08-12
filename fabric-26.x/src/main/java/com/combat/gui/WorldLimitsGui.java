package com.combat.gui;

import com.combat.CombatConfig;
import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.LoreComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorldLimitsGui extends ChestGui {
    private final List<String> worldLimitItemIds = new ArrayList<>();

    public WorldLimitsGui(ServerPlayer player) {
        super(player, "World Item Limits", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        worldLimitItemIds.clear();

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
        add.set(DataComponents.CUSTOM_NAME, Component.literal("Add World Limited Item").withStyle(ChatFormatting.GREEN));
        inventory.setItem(35, add);

        int slot = 9;
        for (Map.Entry<String, CombatConfig.WorldLimitedItem> entry : ConfigManager.getConfig().worldLimits.entrySet()) {
            if (slot >= 27) break;
            String itemId = entry.getKey();
            CombatConfig.WorldLimitedItem wli = entry.getValue();

            ItemStack itemStack;
            try {
                itemStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
            } catch (Exception e) {
                itemStack = new ItemStack(Items.BARRIER);
            }
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(itemId).withStyle(ChatFormatting.GOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Global Server Limit: " + wli.maxCount).withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Configure").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            itemStack.set(DataComponents.LORE, new LoreComponent(loreLines));

            inventory.setItem(slot, itemStack);
            worldLimitItemIds.add(itemId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ClickType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 35) {
            new AnvilInputGui(player, "Enter Item ID", "minecraft:mace") {
                @Override
                protected void handleInput(String inputItemId) {
                    new AnvilInputGui(player, "Enter Max Global Count", "1") {
                        @Override
                        protected void handleInput(String inputCount) {
                            try {
                                int count = Integer.parseInt(inputCount.trim());
                                if (count <= 0) {
                                    player.sendSystemMessage(Component.literal("Limit must be positive!").withStyle(ChatFormatting.RED));
                                } else {
                                    CombatConfig.WorldLimitedItem wli = new CombatConfig.WorldLimitedItem();
                                    wli.maxCount = count;
                                    ConfigManager.getConfig().worldLimits.put(inputItemId.trim(), wli);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Invalid count!").withStyle(ChatFormatting.RED));
                            }
                            new WorldLimitsGui(player).open();
                        }
                    }.open();
                }
            }.open();
        } else if (slotId >= 9 && slotId < 9 + worldLimitItemIds.size()) {
            String itemId = worldLimitItemIds.get(slotId - 9);
            new ChestGui(player, "Configure: " + itemId, 1) {
                @Override
                protected void setupItems() {
                    ItemStack edit = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                    edit.set(DataComponents.CUSTOM_NAME, Component.literal("Change Global Count").withStyle(ChatFormatting.GREEN));
                    inventory.setItem(2, edit);

                    ItemStack remove = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                    remove.set(DataComponents.CUSTOM_NAME, Component.literal("Remove World Limit").withStyle(ChatFormatting.RED));
                    inventory.setItem(6, remove);

                    ItemStack cancel = new ItemStack(Items.ARROW);
                    cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(ChatFormatting.GRAY));
                    inventory.setItem(4, cancel);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, ClickType actionType) {
                    if (subSlotId == 2) {
                        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
                        int currentCount = wli != null ? wli.maxCount : 1;
                        new AnvilInputGui(player, "Set Max Global Count", String.valueOf(currentCount)) {
                            @Override
                            protected void handleInput(String inputCount) {
                                try {
                                    int count = Integer.parseInt(inputCount.trim());
                                    if (count <= 0) {
                                        player.sendSystemMessage(Component.literal("Limit must be positive!").withStyle(ChatFormatting.RED));
                                    } else {
                                        CombatConfig.WorldLimitedItem item = ConfigManager.getConfig().worldLimits.computeIfAbsent(itemId, k -> new CombatConfig.WorldLimitedItem());
                                        item.maxCount = count;
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("Invalid count!").withStyle(ChatFormatting.RED));
                                }
                                new WorldLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        ConfigManager.getConfig().worldLimits.remove(itemId);
                        ConfigManager.save();
                        player.sendSystemMessage(Component.literal("Removed world limit for " + itemId).withStyle(ChatFormatting.YELLOW));
                        new WorldLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new WorldLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
