package com.combat.gui;

import com.combat.ConfigManager;
import com.combat.PlayerData;
import com.combat.DataManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class RankedGui extends ChestGui {

    public RankedGui(ServerPlayer player) {
        super(player, "Top 10 Ranked Buffs Config", 3);
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();

        ItemStack border = new ItemStack(Items.GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(18 + i, border);
        }

        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Main Menu").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(18, back);

        ItemStack toggle = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(ConfigManager.getConfig().rankedSystemEnabled ? "minecraft:lime_dye" : "minecraft:gray_dye")).map(net.minecraft.core.Holder::value).orElse(net.minecraft.world.item.Items.AIR));
        toggle.set(DataComponents.CUSTOM_NAME, Component.literal("Ranked System: " + 
            (ConfigManager.getConfig().rankedSystemEnabled ? "ENABLED" : "DISABLED"))
            .withStyle(ConfigManager.getConfig().rankedSystemEnabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> toggleLore = new ArrayList<>();
        toggleLore.add(Component.literal("Click to toggle ranked system").withStyle(ChatFormatting.YELLOW));
        toggle.set(DataComponents.LORE, new ItemLore(toggleLore));
        inventory.setItem(26, toggle);

        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 22;
            int targetHealth = Math.max(2, 40 - 2 * (pos - 1));
            List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());

            ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
            skull.set(DataComponents.CUSTOM_NAME, 
                Component.literal("Rank Position #" + pos).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Potion Effects: " + (effects.isEmpty() ? "None" : String.join(", ", effects))).withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Configure Effects").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            skull.set(DataComponents.LORE, new ItemLore(loreLines));

            inventory.setItem(slot, skull);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
            return;
        }

        if (slotId == 26) {
            ConfigManager.getConfig().rankedSystemEnabled = !ConfigManager.getConfig().rankedSystemEnabled;
            ConfigManager.save();
            if (com.combat.CombatMod.serverInstance != null) {
                com.combat.CombatMod.serverInstance.getPlayerList().getPlayers().forEach(p -> {
                    net.minecraft.world.scores.Scoreboard scoreboard = com.combat.CombatMod.serverInstance.getScoreboard();
                    String teamName = "c_team_" + p.getUUID().toString().substring(0, 12);
                    net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                    if (team != null) {
                        if (!ConfigManager.getConfig().rankedSystemEnabled) {
                            team.setPlayerPrefix(Component.literal(""));
                        } else {
                            PlayerData data = DataManager.getOrCreatePlayerData(p.getUUID());
                            String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
                            team.setPlayerPrefix(Component.literal(prefixStr).withStyle(ChatFormatting.GOLD));
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
                    backArrow.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Ranks").withStyle(ChatFormatting.YELLOW));
                    inventory.setItem(0, backArrow);

                    int targetHealth = Math.max(2, 40 - 2 * (pos - 1));
                    ItemStack hpItem = new ItemStack(Items.APPLE);
                    hpItem.set(DataComponents.CUSTOM_NAME, Component.literal("Dynamic Max Health").withStyle(ChatFormatting.RED));
                    List<Component> hpLore = new ArrayList<>();
                    hpLore.add(Component.literal("Dynamic Health: " + (targetHealth / 2.0) + " hearts (" + targetHealth + " HP)").withStyle(ChatFormatting.GRAY));
                    hpLore.add(Component.literal("Automatically calculated based on rank").withStyle(ChatFormatting.DARK_GRAY));
                    hpItem.set(DataComponents.LORE, new ItemLore(hpLore));
                    inventory.setItem(2, hpItem);

                    List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new ArrayList<>());
                    boolean hasSpeed = effects.contains("speed");
                    ItemStack speedItem = new ItemStack(Items.SUGAR);
                    speedItem.set(DataComponents.CUSTOM_NAME, Component.literal("Speed Effect").withStyle(ChatFormatting.AQUA));
                    List<Component> speedLore = new ArrayList<>();
                    speedLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                        .append(hasSpeed ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED)));
                    speedLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW));
                    speedItem.set(DataComponents.LORE, new ItemLore(speedLore));
                    inventory.setItem(4, speedItem);

                    boolean hasStrength = effects.contains("strength");
                    ItemStack strengthItem = new ItemStack(Items.BLAZE_POWDER);
                    strengthItem.set(DataComponents.CUSTOM_NAME, Component.literal("Strength Effect").withStyle(ChatFormatting.DARK_RED));
                    List<Component> strengthLore = new ArrayList<>();
                    strengthLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                        .append(hasStrength ? Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN) : Component.literal("INACTIVE").withStyle(ChatFormatting.RED)));
                    strengthLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW));
                    strengthItem.set(DataComponents.LORE, new ItemLore(strengthLore));
                    inventory.setItem(6, strengthItem);
                }

                @Override
                protected void handleSlotClick(int subSlotId, int clickData, ContainerInput actionType) {
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
