package com.combat.gui;

import com.combat.ConfigManager;
import com.combat.PlayerData;
import com.combat.DataManager;
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

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setStack(i, border);
            inventory.setStack(18 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(18, back);

        ItemStack toggle = new ItemStack(ConfigManager.getConfig().rankedSystemEnabled ? Items.LIME_DYE : Items.GRAY_DYE);
        toggle.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ranked System: " + 
            (ConfigManager.getConfig().rankedSystemEnabled ? "ENABLED" : "DISABLED"))
            .formatted(ConfigManager.getConfig().rankedSystemEnabled ? Formatting.GREEN : Formatting.RED, Formatting.BOLD));
        List<Text> toggleLore = new ArrayList<>();
        toggleLore.add(Text.literal("Click to toggle ranked system").formatted(Formatting.YELLOW));
        toggle.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(toggleLore));
        inventory.setStack(26, toggle);

        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 22;
            int targetHealth = Math.max(2, 40 - 2 * (pos - 1));
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());

            ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
            skull.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, 
                Text.literal("Rank Position #" + pos).formatted(Formatting.GOLD, Formatting.BOLD));

            List<Text> loreLines = new ArrayList<>();
            loreLines.add(Text.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").formatted(Formatting.GRAY));
            loreLines.add(Text.literal("Potion Effects: " + (effects.isEmpty() ? "None" : String.join(", ", effects))).formatted(Formatting.GRAY));
            loreLines.add(Text.literal("Click to Configure Effects").formatted(Formatting.YELLOW, Formatting.ITALIC));
            skull.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(loreLines));

            inventory.setStack(slot, skull);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
            return;
        }

        if (slotId == 26) {
            ConfigManager.getConfig().rankedSystemEnabled = !ConfigManager.getConfig().rankedSystemEnabled;
            ConfigManager.save();
            if (com.combat.CombatMod.serverInstance != null) {
                com.combat.CombatMod.serverInstance.getPlayerManager().getPlayerList().forEach(p -> {
                    net.minecraft.scoreboard.Scoreboard scoreboard = com.combat.CombatMod.serverInstance.getScoreboard();
                    String teamName = "c_team_" + p.getUuid().toString().substring(0, 12);
                    net.minecraft.scoreboard.Team team = scoreboard.getTeam(teamName);
                    if (team != null) {
                        if (!ConfigManager.getConfig().rankedSystemEnabled) {
                            team.setPrefix(Text.literal("").formatted(Formatting.GOLD));
                        } else {
                            PlayerData data = DataManager.getOrCreatePlayerData(p.getUuid());
                            String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
                            team.setPrefix(Text.literal(prefixStr).formatted(Formatting.GOLD));
                        }
                    }
                });
            }
            setupItems();
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
                    backArrow.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Ranks").formatted(Formatting.YELLOW));
                    inventory.setStack(0, backArrow);

                    int targetHealth = Math.max(2, 40 - 2 * (pos - 1));
                    ItemStack hpItem = new ItemStack(Items.APPLE);
                    hpItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Dynamic Max Health").formatted(Formatting.RED));
                    List<Text> hpLore = new ArrayList<>();
                    hpLore.add(Text.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").formatted(Formatting.GRAY));
                    hpLore.add(Text.literal("Automatically calculated based on rank").formatted(Formatting.DARK_GRAY));
                    hpItem.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(hpLore));
                    inventory.setStack(2, hpItem);

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
