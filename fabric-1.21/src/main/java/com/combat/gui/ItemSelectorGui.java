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

public class ItemSelectorGui extends ChestGui {
    private final int page;
    private final String query;
    private final boolean isWorldLimit;
    private final List<net.minecraft.item.Item> filteredItems = new ArrayList<>();

    public ItemSelectorGui(ServerPlayerEntity player, int page, String query, boolean isWorldLimit) {
        super(player, query.isEmpty() ? "Select Item" : "Search: " + query, 6);
        this.page = page;
        this.query = query.trim().toLowerCase();
        this.isWorldLimit = isWorldLimit;
    }

    @Override
    protected void setupItems() {
        inventory.clear();
        filteredItems.clear();

        for (Identifier id : Registries.ITEM.getIds()) {
            net.minecraft.item.Item item = Registries.ITEM.get(id);
            if (item == Items.AIR) continue;
            String idStr = id.toString();
            if (query.isEmpty() || idStr.contains(query)) {
                filteredItems.add(item);
            }
        }

        filteredItems.sort((a, b) -> {
            String idA = Registries.ITEM.getId(a).toString();
            String idB = Registries.ITEM.getId(b).toString();
            return idA.compareTo(idB);
        });

        int startIdx = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = startIdx + i;
            if (idx < filteredItems.size()) {
                ItemStack stack = new ItemStack(filteredItems.get(idx));
                inventory.setStack(i, stack);
            }
        }

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        inventory.setStack(46, border);
        inventory.setStack(47, border);
        inventory.setStack(51, border);
        inventory.setStack(52, border);

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Previous Page").formatted(Formatting.YELLOW));
            inventory.setStack(45, prev);
        } else {
            inventory.setStack(45, border);
        }

        if ((page + 1) * 45 < filteredItems.size()) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Next Page").formatted(Formatting.YELLOW));
            inventory.setStack(53, next);
        } else {
            inventory.setStack(53, border);
        }

        ItemStack back = new ItemStack(Items.BARRIER);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(isWorldLimit ? "Back to World Limits" : "Back to Limits").formatted(Formatting.RED));
        inventory.setStack(49, back);

        ItemStack search = new ItemStack(Items.NAME_TAG);
        search.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Search Items").formatted(Formatting.GREEN));
        if (!query.isEmpty()) {
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Current Search: " + query).formatted(Formatting.GRAY));
            search.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
        }
        inventory.setStack(48, search);

        if (!query.isEmpty()) {
            ItemStack clear = new ItemStack(Items.MILK_BUCKET);
            clear.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Clear Search").formatted(Formatting.YELLOW));
            inventory.setStack(50, clear);
        } else {
            inventory.setStack(50, border);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId >= 0 && slotId < 45) {
            int startIdx = page * 45;
            int idx = startIdx + slotId;
            if (idx < filteredItems.size()) {
                net.minecraft.item.Item item = filteredItems.get(idx);
                String itemId = Registries.ITEM.getId(item).toString();
                if (isWorldLimit) {
                    new AnvilInputGui(player, "Set Max Global Count", "1") {
                        @Override
                        protected void handleInput(String inputCount) {
                            try {
                                int count = Integer.parseInt(inputCount.trim());
                                if (count <= 0) {
                                    player.sendMessage(Text.literal("Limit must be positive!").formatted(Formatting.RED), false);
                                } else {
                                    com.combat.CombatConfig.WorldLimitedItem wli = new com.combat.CombatConfig.WorldLimitedItem();
                                    wli.maxCount = count;
                                    ConfigManager.getConfig().worldLimits.put(itemId, wli);
                                    ConfigManager.save();
                                    player.sendMessage(Text.literal("Set global limit for " + itemId + " to " + count).formatted(Formatting.GREEN), false);
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid count!").formatted(Formatting.RED), false);
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
                                player.sendMessage(Text.literal("Set limit for " + itemId + " to " + amount).formatted(Formatting.GREEN), false);
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
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
