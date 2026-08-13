package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryLimitsGui extends ChestGui {
    private final List<String> itemIds = new ArrayList<>();

    public InventoryLimitsGui(ServerPlayer player) {
        super(player, "Inventory Limits Config", 4);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        itemIds.clear();

        ItemStack border = new ItemStack(Items.GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(27 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Main Menu").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(27, back);

        boolean combatOnly = ConfigManager.getConfig().limitsOnlyInCombat;
        net.minecraft.world.item.Item iconItem = combatOnly ? Items.REDSTONE : BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:lime_dye")).map(r -> r.value()).orElse(Items.EMERALD);
        ItemStack modeToggle = new ItemStack(iconItem);
        modeToggle.set(DataComponents.CUSTOM_NAME, Component.literal("Item Limits Mode: " + (combatOnly ? "ONLY IN COMBAT" : "ALWAYS ACTIVE"))
            .withStyle(combatOnly ? ChatFormatting.GOLD : ChatFormatting.GREEN, ChatFormatting.BOLD));
        List<Component> modeLore = new ArrayList<>();
        modeLore.add(Component.literal("Click to switch mode").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        modeToggle.set(DataComponents.LORE, new ItemLore(modeLore));
        inventory.setItem(31, modeToggle);

        ItemStack add = new ItemStack(Items.EMERALD);
        add.set(DataComponents.CUSTOM_NAME, Component.literal("Add New Item Limit").withStyle(ChatFormatting.GREEN));
        inventory.setItem(35, add);

        int slot = 9;
        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().itemLimits.entrySet()) {
            if (slot >= 27) break;
            String itemId = entry.getKey();
            int limit = entry.getValue();

            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).map(r -> r.value()).orElse(net.minecraft.world.item.Items.AIR));
            item.set(DataComponents.CUSTOM_NAME, Component.literal(itemId).withStyle(ChatFormatting.GOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Current Limit: " + limit).withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Edit/Remove").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            item.set(DataComponents.LORE, new ItemLore(loreLines));

            inventory.setItem(slot, item);
            itemIds.add(itemId);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
        } else if (slotId == 31) {
            ConfigManager.getConfig().limitsOnlyInCombat = !ConfigManager.getConfig().limitsOnlyInCombat;
            ConfigManager.save();
            player.sendSystemMessage(Component.literal("Item Limits Mode set to: " + (ConfigManager.getConfig().limitsOnlyInCombat ? "ONLY IN COMBAT" : "ALWAYS ACTIVE")).withStyle(ChatFormatting.GREEN));
            new InventoryLimitsGui(player).open();
        } else if (slotId == 35) {
            new ItemSelectorGui(player, 0, "", false).open();
        } else if (slotId >= 9 && slotId < 9 + itemIds.size()) {
            String itemId = itemIds.get(slotId - 9);
            new ChestGui(player, "Edit: " + itemId, 1) {
                @Override
                protected void setupItems() {
                    ItemStack edit = new ItemStack(Items.EMERALD);
                    edit.set(DataComponents.CUSTOM_NAME, Component.literal("Change Limit").withStyle(ChatFormatting.GREEN));
                    inventory.setItem(2, edit);

                    ItemStack remove = new ItemStack(Items.REDSTONE);
                    remove.set(DataComponents.CUSTOM_NAME, Component.literal("Delete Limit").withStyle(ChatFormatting.RED));
                    inventory.setItem(6, remove);

                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(ChatFormatting.GRAY));
                    inventory.setItem(4, backArrow);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType) {
                    if (subSlotId == 2) {
                        new AnvilInputGui(player, "Change Limit", String.valueOf(ConfigManager.getConfig().itemLimits.get(itemId))) {
                            @Override
                            protected void handleInput(String inputAmount) {
                                try {
                                    int amount = Integer.parseInt(inputAmount.trim());
                                    ConfigManager.getConfig().itemLimits.put(itemId, amount);
                                    ConfigManager.save();
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("Invalid amount!").withStyle(ChatFormatting.RED));
                                }
                                new InventoryLimitsGui(player).open();
                            }
                        }.open();
                    } else if (subSlotId == 6) {
                        ConfigManager.getConfig().itemLimits.remove(itemId);
                        ConfigManager.save();
                        player.sendSystemMessage(Component.literal("Removed limit for " + itemId).withStyle(ChatFormatting.YELLOW));
                        new InventoryLimitsGui(player).open();
                    } else if (subSlotId == 4) {
                        new InventoryLimitsGui(player).open();
                    }
                }
            }.open();
        }
    }
}
