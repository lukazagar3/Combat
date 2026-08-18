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

        boolean effectsEnabled = ConfigManager.getConfig().topRanksEffectsEnabled;
        ItemStack effectsToggle = new ItemStack(Items.BREWING_STAND);
        effectsToggle.set(DataComponents.CUSTOM_NAME, Component.literal("Top 3 Rank Effects").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        List<Component> effectsLore = new ArrayList<>();
        effectsLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
            .append(effectsEnabled ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        effectsLore.add(Component.literal("Click to Toggle Effects").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        effectsToggle.set(DataComponents.LORE, new ItemLore(effectsLore));
        inventory.setItem(22, effectsToggle);

        ItemStack toggle = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(ConfigManager.getConfig().rankedSystemEnabled ? "minecraft:lime_dye" : "minecraft:gray_dye")).map(net.minecraft.core.Holder::value).orElse(net.minecraft.world.item.Items.AIR));
        toggle.set(DataComponents.CUSTOM_NAME, Component.literal("Ranked System: " + 
            (ConfigManager.getConfig().rankedSystemEnabled ? "ENABLED" : "DISABLED"))
            .withStyle(ConfigManager.getConfig().rankedSystemEnabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> toggleLore = new ArrayList<>();
        toggleLore.add(Component.literal("Click to toggle ranked system").withStyle(ChatFormatting.YELLOW));
        toggle.set(DataComponents.LORE, new ItemLore(toggleLore));
        inventory.setItem(26, toggle);

        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 23;
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

        if (slotId == 22) {
            ConfigManager.getConfig().topRanksEffectsEnabled = !ConfigManager.getConfig().topRanksEffectsEnabled;
            ConfigManager.save();
            setupItems();
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
        } else if (slotId == 23) {
            selectedPos = 10;
        }

        if (selectedPos != -1) {
            new RankPositionBuffsGui(player, selectedPos).open();
        }
    }
}
