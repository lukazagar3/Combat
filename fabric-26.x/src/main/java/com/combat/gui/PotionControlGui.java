package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.LoreComponent;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PotionControlGui extends ChestGui {
    private final List<String> defaultPotionIds = new ArrayList<>();
    private final Map<String, Holder<Potion>> potionRegistryMap = new HashMap<>();

    public PotionControlGui(ServerPlayer player) {
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
        inventory.clearContent();

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
        add.set(DataComponents.CUSTOM_NAME, Component.literal("Add Custom Potion Limit").withStyle(ChatFormatting.GREEN));
        inventory.setItem(35, add);

        List<String> toRender = new ArrayList<>(defaultPotionIds);
        for (String key : ConfigManager.getConfig().potionLimits.keySet()) {
            if (!toRender.contains(key)) {
                toRender.add(key);
            }
        }

        int slot = 9;
        for (String potionId : toRender) {
            if (slot >= 27) break;
            int limit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);

            ItemStack potionStack;
            if (potionRegistryMap.containsKey(potionId)) {
                potionStack = PotionContents.createItemStack(Items.POTION, potionRegistryMap.get(potionId));
            } else {
                potionStack = new ItemStack(Items.POTION);
            }

            potionStack.set(DataComponents.CUSTOM_NAME, 
                Component.literal(potionId).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<Component> loreLines = new ArrayList<>();
            if (limit == -1) {
                loreLines.add(Component.literal("Brewing: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            } else {
                loreLines.add(Component.literal("Brewing: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
                loreLines.add(Component.literal("Max Allowed Level: " + limit).withStyle(ChatFormatting.GRAY));
            }
            loreLines.add(Component.literal("Click to Configure").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            potionStack.set(DataComponents.LORE, new LoreComponent(loreLines));

            inventory.setItem(slot, potionStack);
            slot++;
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ClickType actionType) {
        if (slotId == 27) {
            new CombatMenuGui(player).open();
            return;
        }

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
                                    player.sendSystemMessage(Component.literal("Invalid level limit!").withStyle(ChatFormatting.RED));
                                } else {
                                    ConfigManager.getConfig().potionLimits.put(inputPotionId.trim(), level);
                                    ConfigManager.save();
                                }
                            } catch (NumberFormatException e) {
                                player.sendSystemMessage(Component.literal("Invalid level!").withStyle(ChatFormatting.RED));
                            }
                            new PotionControlGui(player).open();
                        }
                    }.open();
                }
            }.open();
            return;
        }

        List<String> toRender = new ArrayList<>(defaultPotionIds);
        for (String key : ConfigManager.getConfig().potionLimits.keySet()) {
            if (!toRender.contains(key)) {
                toRender.add(key);
            }
        }

        int index = slotId - 9;
        if (index >= 0 && index < toRender.size()) {
            String potionId = toRender.get(index);
            new ChestGui(player, "Configure Potion: " + potionId, 1) {
                @Override
                protected void setupItems() {
                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Potions").withStyle(ChatFormatting.YELLOW));
                    inventory.setItem(0, backArrow);

                    int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                    boolean isBrewingDisabled = (currentLimit == -1);

                    ItemStack toggleItem = new ItemStack(isBrewingDisabled ? Items.GREEN_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE);
                    toggleItem.set(DataComponents.CUSTOM_NAME, 
                        isBrewingDisabled ? Component.literal("Enable Brewing").withStyle(ChatFormatting.GREEN) : Component.literal("Disable Brewing").withStyle(ChatFormatting.RED));
                    List<Component> toggleLore = new ArrayList<>();
                    toggleLore.add(Component.literal("Current Brewing Status: ").withStyle(ChatFormatting.GRAY)
                        .append(isBrewingDisabled ? Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD) : Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
                    toggleLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW));
                    toggleItem.set(DataComponents.LORE, new LoreComponent(toggleLore));
                    inventory.setItem(2, toggleItem);

                    ItemStack maxLevelItem = new ItemStack(Items.GLOWSTONE_DUST);
                    maxLevelItem.set(DataComponents.CUSTOM_NAME, Component.literal("Set Max Allowed Level").withStyle(ChatFormatting.GOLD));
                    List<Component> maxLevelLore = new ArrayList<>();
                    maxLevelLore.add(Component.literal("Current Limit: " + (isBrewingDisabled ? "N/A (Disabled)" : String.valueOf(currentLimit))).withStyle(ChatFormatting.GRAY));
                    maxLevelLore.add(Component.literal("Click to Edit").withStyle(ChatFormatting.YELLOW));
                    maxLevelItem.set(DataComponents.LORE, new LoreComponent(maxLevelLore));
                    inventory.setItem(6, maxLevelItem);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, ClickType actionType) {
                    if (subSlotId == 0) {
                        new PotionControlGui(player).open();
                    } else if (subSlotId == 2) {
                        int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                        if (currentLimit == -1) {
                            ConfigManager.getConfig().potionLimits.put(potionId, 2);
                        } else {
                            ConfigManager.getConfig().potionLimits.put(potionId, -1);
                        }
                        ConfigManager.save();
                        setupItems();
                    } else if (subSlotId == 6) {
                        int currentLimit = ConfigManager.getConfig().potionLimits.getOrDefault(potionId, 2);
                        int defaultInput = currentLimit == -1 ? 1 : currentLimit;
                        new AnvilInputGui(player, "Set Max Level for " + potionId, String.valueOf(defaultInput)) {
                            @Override
                            protected void handleInput(String inputLevel) {
                                try {
                                    int level = Integer.parseInt(inputLevel.trim());
                                    if (level < -1) {
                                        player.sendSystemMessage(Component.literal("Invalid level limit!").withStyle(ChatFormatting.RED));
                                    } else {
                                        ConfigManager.getConfig().potionLimits.put(potionId, level);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("Invalid level!").withStyle(ChatFormatting.RED));
                                }
                                open();
                            }
                        }.open();
                    }
                }
            }.open();
        }
    }
}
