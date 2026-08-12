package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class RankedGui extends ChestGui {

    public RankedGui(ServerPlayerEntity player) {
        super(player, "Top 10 Ranked Buffs Config", 3);
    }

    @Override
    protected void setupItems() {
        inventory.clear();

        // Border
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(18 + i, border);
        }

        // Back Arrow
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(18, back);

        // Render positions #1 to #10
        // We will put them in slots 9 to 17 (which is 9 slots, positions 1 to 9), and slot 26 for position 10.
        // Wait, slot 9 to 17 is 9 slots. Position 10 can be in slot 19 (next row, after back arrow).
        // Let's place them neatly:
        // Row 1: #1 to #9 -> slots 9 to 17
        // Row 2: #10 -> slot 22 (centered in bottom row next to back)
        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 22;
            int hpBoost = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());

            ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
            skull.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal("Rank Position #" + pos).formatted(Formatting.GOLD, Formatting.BOLD));
            
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Extra Health: +" + (hpBoost / 2.0) + " hearts (" + hpBoost + " HP)").formatted(Formatting.GRAY));
            lore.add(Text.literal("Potion Effects: " + (effects.isEmpty() ? "None" : String.join(", ", effects))).formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to Configure").formatted(Formatting.YELLOW, Formatting.ITALIC));
            skull.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(slot, skull);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
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
            // Open submenu for this position
            new ChestGui(player, "Buffs for Position #" + pos, 1) {
                @Override
                protected void setupItems() {
                    // Back
                    ItemStack backArrow = new ItemStack(Items.ARROW);
                    backArrow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Ranks").formatted(Formatting.YELLOW));
                    inventory.setStack(0, backArrow);

                    // Health Boost config (Apple)
                    int currentHp = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
                    ItemStack hpItem = new ItemStack(Items.RED_DYE);
                    hpItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Max Health Boost").formatted(Formatting.RED));
                    List<Text> hpLore = new ArrayList<>();
                    hpLore.add(Text.literal("Current: +" + currentHp + " HP").formatted(Formatting.GRAY));
                    hpLore.add(Text.literal("Click to Edit").formatted(Formatting.YELLOW));
                    hpItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(hpLore));
                    inventory.setStack(2, hpItem);

                    // Speed effect toggle (Sugar)
                    List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                    boolean hasSpeed = effects.contains("speed");
                    ItemStack speedItem = new ItemStack(Items.SUGAR);
                    speedItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Speed Effect").formatted(Formatting.AQUA));
                    List<Text> speedLore = new ArrayList<>();
                    speedLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
                        .append(hasSpeed ? Text.literal("ACTIVE").formatted(Formatting.GREEN) : Text.literal("INACTIVE").formatted(Formatting.RED)));
                    speedLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW));
                    speedItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(speedLore));
                    inventory.setStack(4, speedItem);

                    // Strength effect toggle (Blaze Powder)
                    boolean hasStrength = effects.contains("strength");
                    ItemStack strengthItem = new ItemStack(Items.BLAZE_POWDER);
                    strengthItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Strength Effect").formatted(Formatting.DARK_RED));
                    List<Text> strengthLore = new ArrayList<>();
                    strengthLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
                        .append(hasStrength ? Text.literal("ACTIVE").formatted(Formatting.GREEN) : Text.literal("INACTIVE").formatted(Formatting.RED)));
                    strengthLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW));
                    strengthItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(strengthLore));
                    inventory.setStack(6, strengthItem);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, SlotActionType actionType) {
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
                                        player.sendMessage(Text.literal("HP Boost cannot be negative!").formatted(Formatting.RED), false);
                                    } else {
                                        ConfigManager.getConfig().rankHealthBoosts.put(pos, hp);
                                        ConfigManager.save();
                                    }
                                } catch (NumberFormatException e) {
                                    player.sendMessage(Text.literal("Invalid HP value!").formatted(Formatting.RED), false);
                                }
                                open(); // Reopen submenu
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
                        setupItems(); // Refresh submenu
                    } else if (subSlotId == 6) {
                        List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                        if (effects.contains("strength")) {
                            effects.remove("strength");
                        } else {
                            effects.add("strength");
                        }
                        ConfigManager.save();
                        setupItems(); // Refresh submenu
                    }
                }
            }.open();
        }
    }
}
