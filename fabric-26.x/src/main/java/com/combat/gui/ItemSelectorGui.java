package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class ItemSelectorGui extends ChestGui {
    private final int page;
    private final String query;
    private final boolean isWorldLimit;
    private final List<net.minecraft.world.item.Item> filteredItems = new ArrayList<>();

    public ItemSelectorGui(ServerPlayer player, int page, String query, boolean isWorldLimit) {
        super(player, query.isEmpty() ? "Select Item" : "Search: " + query, 6);
        this.page = page;
        this.query = query.trim().toLowerCase();
        this.isWorldLimit = isWorldLimit;
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        filteredItems.clear();

        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            net.minecraft.world.item.Item item = entry.getValue();
            if (item == Items.AIR) continue;
            String idStr = entry.getKey().identifier().toString();
            if (query.isEmpty() || idStr.contains(query)) {
                filteredItems.add(item);
            }
        }

        filteredItems.sort((a, b) -> {
            String idA = BuiltInRegistries.ITEM.getKey(a).toString();
            String idB = BuiltInRegistries.ITEM.getKey(b).toString();
            return idA.compareTo(idB);
        });

        int startIdx = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = startIdx + i;
            if (idx < filteredItems.size()) {
                ItemStack stack = new ItemStack(filteredItems.get(idx));
                inventory.setItem(i, stack);
            }
        }

        ItemStack border = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:gray_stained_glass_pane")).map(net.minecraft.core.Holder::value).orElse(Items.AIR));
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        inventory.setItem(46, border);
        inventory.setItem(47, border);
        inventory.setItem(51, border);
        inventory.setItem(52, border);

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page").withStyle(ChatFormatting.YELLOW));
            inventory.setItem(45, prev);
        } else {
            inventory.setItem(45, border);
        }

        if ((page + 1) * 45 < filteredItems.size()) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page").withStyle(ChatFormatting.YELLOW));
            inventory.setItem(53, next);
        } else {
            inventory.setItem(53, border);
        }

        ItemStack back = new ItemStack(Items.BARRIER);
        back.set(DataComponents.CUSTOM_NAME, Component.literal(isWorldLimit ? "Back to World Limits" : "Back to Limits").withStyle(ChatFormatting.RED));
        inventory.setItem(49, back);

        ItemStack search = new ItemStack(Items.NAME_TAG);
        search.set(DataComponents.CUSTOM_NAME, Component.literal("Search Items").withStyle(ChatFormatting.GREEN));
        if (!query.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Current Search: " + query).withStyle(ChatFormatting.GRAY));
            search.set(DataComponents.LORE, new ItemLore(lore));
        }
        inventory.setItem(48, search);

        if (!query.isEmpty()) {
            ItemStack clear = new ItemStack(Items.MILK_BUCKET);
            clear.set(DataComponents.CUSTOM_NAME, Component.literal("Clear Search").withStyle(ChatFormatting.YELLOW));
            inventory.setItem(50, clear);
        } else {
            inventory.setItem(50, border);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId >= 0 && slotId < 45) {
            int startIdx = page * 45;
            int idx = startIdx + slotId;
            if (idx < filteredItems.size()) {
                net.minecraft.world.item.Item item = filteredItems.get(idx);
                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                if (isWorldLimit) {
                    new AnvilInputGui(player, "Set Max Global Count", "1") {
                        @Override
                        protected void handleInput(String inputCount) {
                            try {
                                int count = Integer.parseInt(inputCount.trim());
                                if (count <= 0) {
                                    player.sendSystemMessage(Component.literal("Limit must be positive!").withStyle(ChatFormatting.RED));
                                } else {
                                    com.combat.CombatConfig.WorldLimitedItem wli = new com.combat.CombatConfig.WorldLimitedItem();
                                    wli.maxCount = count;
                                    ConfigManager.getConfig().worldLimits.put(itemId, wli);
                                    ConfigManager.save();
                                    player.sendSystemMessage(Component.literal("Set global limit for " + itemId + " to " + count).withStyle(ChatFormatting.GREEN));
                                }
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Invalid count!").withStyle(ChatFormatting.RED));
                            }
                            new WorldLimitsGui(player).open();
                        }
                    }.open();
                } else {
                    new AnvilInputGui(player, "Amount Selector", "64") {
                        @Override
                        protected void handleInput(String inputAmount) {
                            try {
                                int amount = Integer.parseInt(inputAmount.trim());
                                ConfigManager.getConfig().itemLimits.put(itemId, amount);
                                ConfigManager.save();
                                player.sendSystemMessage(Component.literal("Set limit for " + itemId + " to " + amount).withStyle(ChatFormatting.GREEN));
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Invalid amount!").withStyle(ChatFormatting.RED));
                            }
                            new InventoryLimitsGui(player).open();
                        }
                    }.open();
                }
            }
        } else if (slotId == 45) {
            if (page > 0) {
                new ItemSelectorGui(player, page - 1, query, isWorldLimit).open();
            }
        } else if (slotId == 53) {
            if ((page + 1) * 45 < filteredItems.size()) {
                new ItemSelectorGui(player, page + 1, query, isWorldLimit).open();
            }
        } else if (slotId == 49) {
            if (isWorldLimit) {
                new WorldLimitsGui(player).open();
            } else {
                new InventoryLimitsGui(player).open();
            }
        } else if (slotId == 48) {
            new AnvilInputGui(player, "Search Item Name", "") {
                @Override
                protected void handleInput(String searchInput) {
                    new ItemSelectorGui(player, 0, searchInput, isWorldLimit).open();
                }
            }.open();
        } else if (slotId == 50) {
            if (!query.isEmpty()) {
                new ItemSelectorGui(player, 0, "", isWorldLimit).open();
            }
        }
    }
}
