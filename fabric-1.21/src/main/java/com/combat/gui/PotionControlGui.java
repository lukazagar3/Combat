package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PotionControlGui extends ChestGui {
    private final List<String> defaultPotionIds = new ArrayList<>();
    private final Map<String, net.minecraft.registry.entry.RegistryEntry<Potion>> potionRegistryMap = new HashMap<>();

    public PotionControlGui(ServerPlayerEntity player) {
        super(player, "Potion Control System", 4);
        
        defaultPotionIds.add("minecraft:strength");
        defaultPotionIds.add("minecraft:swiftness");
        defaultPotionIds.add("minecraft:slowness");
        defaultPotionIds.add("minecraft:harming");
        defaultPotionIds.add("minecraft:healing");
        defaultPotionIds.add("minecraft:poison");
        defaultPotionIds.add("minecraft:regeneration");
        defaultPotionIds.add("minecraft:invisibility");

        potionRegistryMap.put("minecraft:strength", Potions.STRENGTH);
        potionRegistryMap.put("minecraft:swiftness", Potions.SWIFTNESS);
        potionRegistryMap.put("minecraft:slowness", Potions.SLOWNESS);
        potionRegistryMap.put("minecraft:harming", Potions.HARMING);
        potionRegistryMap.put("minecraft:healing", Potions.HEALING);
        potionRegistryMap.put("minecraft:poison", Potions.POISON);
        potionRegistryMap.put("minecraft:regeneration", Potions.REGENERATION);
        potionRegistryMap.put("minecraft:invisibility", Potions.INVISIBILITY);
    }

    @Override
    protected void setupItems() {
        inventory.clear();

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

        // Add custom limit button
        ItemStack add = new ItemStack(Items.EMERALD);
        add.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Add Custom Potion Limit").formatted(Formatting.GREEN));
        inventory.setStack(35, add);

        // Render current potion configs
        // Gather all potion IDs we should show: defaults + any custom ones added in config
        List<String> toRender = new ArrayList<>(defaultPotionIds);
        for (String key : ConfigManager.getConfig().potionLimits.keySet()) {
            if (!toRender.contains(key)) {
                toRender.add(key);
            }
        }

        int slot = 9;
        for (String potionId : toRender) {
            if (slot >= 27) break;
            int limit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2); // Default max level II in vanilla

            ItemStack potionStack;
            if (potionRegistryMap.containsKey(potionId)) {
                potionStack = PotionContentsComponent.createStack(Items.POTION, potionRegistryMap.get(potionId));
            } else {
                potionStack = new ItemStack(Items.POTION);
            }

            potionStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal(potionId).formatted(Formatting.GOLD, Formatting.BOLD));

            List<Text> lore = new ArrayList<>();
            if (limit == -1) {
                lore.add(Text.literal("Brewing: ").formatted(Formatting.GRAY)
                    .append(Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
            } else {
                lore.add(Text.literal("Brewing: ").formatted(Formatting.GRAY)
                    .append(Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD)));
                lore.add(Text.literal("Max Allowed Level: " + limit).formatted(Formatting.GRAY));
            }
            lore.add(Text.literal("Click to Configure").formatted(Formatting.YELLOW, Formatting.ITALIC));
            potionStack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(slot, potionStack);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
            return;
        }

        // Handle Add Custom Potion Limit
        if (slotId == 35) {
            new AnvilInputGui(player, "Enter Potion ID", "minecraft:strength") {
                @Override
                protected void handleInput(String inputPotionId) {
                    new AnvilInputGui(player, "Max Level (-1 to Disable)", "1") {
                        @Override
                        protected void handleInput(String inputLevel) {
                            try {
                                int level = Integer.parseInt(inputLevel.trim());
                                if (level < -1) {
                                    player.sendMessage(Text.literal("Invalid level limit!").formatted(Formatting.RED), false);
                                } else {
                                    ConfigManager.getConfig().potionLimits.put(inputPotionId.trim(), level);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(Text.literal("Invalid level!").formatted(Formatting.RED), false);
                            }
                            new PotionControlGui(player).open();
                        }
                    }.open();
                }
            }.open();
            return;
        }

        // Determine which potion was clicked
        List<String> toRender = new ArrayList<>(defaultPotionIds);
        for (String key : ConfigManager.getConfig().potionLimits.keySet()) {
            if (!toRender.contains(key)) {
                toRender.add(key);
            }
        }

        int index = slotId - 9;
        if (index >= 0 && index < toRender.size()) {
            String potionId = toRender.get(index);
            // Open submenu
            new ChestGui(player, "Configure Potion: " + potionId, 1) {
                @Override
                protected void setupItems() {
                    // Back arrow
                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Potions").formatted(Formatting.YELLOW));
                    inventory.setStack(0, backArrow);

                    // Toggle Brewing Enable/Disable (represented by Green / Red Glass Pane)
                    int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                    boolean isBrewingDisabled = (currentLimit == -1);

                    ItemStack toggleItem = new ItemStack(isBrewingDisabled ? Items.GREEN_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE);
                    toggleItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                        isBrewingDisabled ? Text.literal("Enable Brewing").formatted(Formatting.GREEN) : Text.literal("Disable Brewing").formatted(Formatting.RED));
                    List<Text> toggleLore = new ArrayList<>();
                    toggleLore.add(Text.literal("Current Brewing Status: ").formatted(Formatting.GRAY)
                        .append(isBrewingDisabled ? Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD) : Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD)));
                    toggleLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW));
                    toggleItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(toggleLore));
                    inventory.setStack(2, toggleItem);

                    // Set Max Level (represented by Glowstone Dust)
                    ItemStack maxLevelItem = new ItemStack(Items.GLOWSTONE_DUST);
                    maxLevelItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Set Max Allowed Level").formatted(Formatting.GOLD));
                    List<Text> maxLevelLore = new ArrayList<>();
                    maxLevelLore.add(Text.literal("Current Limit: " + (isBrewingDisabled ? "N/A (Disabled)" : String.valueOf(currentLimit))).formatted(Formatting.GRAY));
                    maxLevelLore.add(Text.literal("Click to Edit").formatted(Formatting.YELLOW));
                    maxLevelItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(maxLevelLore));
                    inventory.setStack(6, maxLevelItem);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, SlotActionType actionType) {
                    if (subSlotId == 0) {
                        new PotionControlGui(player).open();
                    } else if (subSlotId == 2) {
                        int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                        if (currentLimit == -1) {
                            // Enable it (set max level to II by default)
                            ConfigManager.getConfig().potionLimits.put(potionId, 2);
                        } else {
                            // Disable it
                            ConfigManager.getConfig().potionLimits.put(potionId, -1);
                        }
                        ConfigManager.save();
                        setupItems(); // Refresh submenu
                    } else if (subSlotId == 6) {
                        int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                        int defaultInput = currentLimit == -1 ? 1 : currentLimit;
                        new AnvilInputGui(player, "Set Max Level for " + potionId, String.valueOf(defaultInput)) {
                            @Override
                            protected void handleInput(String inputLevel) {
                                try {
                                    int level = Integer.parseInt(inputLevel.trim());
                                    if (level < -1) {
                                        player.sendMessage(Text.literal("Invalid level limit!").formatted(Formatting.RED), false);
                                    } else {
                                        ConfigManager.getConfig().potionLimits.put(potionId, level);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendMessage(Text.literal("Invalid level!").formatted(Formatting.RED), false);
                                }
                                open(); // Reopen submenu
                            }
                        }.open();
                    }
                }
            }.open();
        }
    }
}
