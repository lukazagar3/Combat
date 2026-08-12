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

public class RankedGui extends ChestGui {

    public RankedGui(ServerPlayer player) {
        super(player, "Top 10 Ranked Buffs Config", 3);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(18 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Main Menu").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(18, back);

        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 22;
            int hpBoost = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());

            ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
            skull.set(DataComponents.CUSTOM_NAME, 
                Component.literal("Rank Position #" + pos).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Extra Health: +" + (hpBoost / 2.0) + " hearts (" + hpBoost + " HP)").withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Potion Effects: " + (effects.isEmpty() ? "None" : String.join(", ", effects))).withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Configure").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            skull.set(DataComponents.LORE, new LoreComponent(loreLines));

            inventory.setItem(slot, skull);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ClickType actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
            return;
        }

        int selectedPos = -1;
        if (slotId >= 9 && slotId <= 17) {
            selectedPos = slotId - 8;
        } else if (slotId == 22) {
            selectedPos = 10;
        }

        if (selectedPos != -1) {
            final int pos = selectedPos;
            new ChestGui(player, "Buffs for Position #" + pos, 1) {
                @Override
                protected void setupItems() {
                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Ranks").withStyle(ChatFormatting.YELLOW));
                    inventory.setItem(0, backArrow);

                    int currentHp = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
                    ItemStack hpItem = new ItemStack(Items.RED_DYE);
                    hpItem.set(DataComponents.CUSTOM_NAME, Component.literal("Max Health Boost").withStyle(ChatFormatting.RED));
                    List<Component> hpLore = new ArrayList<>();
                    hpLore.add(Component.literal("Current: +" + currentHp + " HP").withStyle(ChatFormatting.GRAY));
                    hpLore.add(Component.literal("Click to Edit").withStyle(ChatFormatting.YELLOW));
                    hpItem.set(DataComponents.LORE, new LoreComponent(hpLore));
                    inventory.setItem(2, hpItem);

                    List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                    boolean hasSpeed = effects.contains("speed");
                    ItemStack speedItem = new ItemStack(Items.SUGAR);
                    speedItem.set(DataComponents.CUSTOM_NAME, Component.literal("Speed Effect").withStyle(ChatFormatting.AQUA));
                    List<Component> speedLore = new ArrayList<>();
                    speedLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                        .append(hasSpeed ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED)));
                    speedLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW));
                    speedItem.set(DataComponents.LORE, new LoreComponent(speedLore));
                    inventory.setItem(4, speedItem);

                    boolean hasStrength = effects.contains("strength");
                    ItemStack strengthItem = new ItemStack(Items.BLAZE_POWDER);
                    strengthItem.set(DataComponents.CUSTOM_NAME, Component.literal("Strength Effect").withStyle(ChatFormatting.DARK_RED));
                    List<Component> strengthLore = new ArrayList<>();
                    strengthLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                        .append(hasStrength ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED)));
                    strengthLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW));
                    strengthItem.set(DataComponents.LORE, new LoreComponent(strengthLore));
                    inventory.setItem(6, strengthItem);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, ClickType actionType) {
                    if (subSlotId == 0) {
                        new RankedGui(player).open();
                    } else if (subSlotId == 2) {
                        int currentHp = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
                        new AnvilInputGui(player, "Set Extra Max HP (e.g. 40)", String.valueOf(currentHp)) {
                            @Override
                            protected void handleInput(String input) {
                                try {
                                    int hp = Integer.parseInt(input.trim());
                                    if (hp < 0) {
                                        player.sendSystemMessage(Component.literal("HP Boost cannot be negative!").withStyle(ChatFormatting.RED));
                                    } else {
                                        ConfigManager.getConfig().rankHealthBoosts.put(pos, hp);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendSystemMessage(Component.literal("Invalid HP value!").withStyle(ChatFormatting.RED));
                                }
                                open();
                            }
                        }.open();
                    } else if (subSlotId == 4) {
                        List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                        if (effects.contains("speed")) {
                            effects.remove("speed");
                        } else {
                            effects.add("speed");
                        }
                        ConfigManager.save();
                        setupItems();
                    } else if (subSlotId == 6) {
                        List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                        if (effects.contains("strength")) {
                            effects.remove("strength");
                        } else {
                            effects.add("strength");
                        }
                        ConfigManager.save();
                        setupItems();
                    }
                }
            }.open();
        }
    }
}
