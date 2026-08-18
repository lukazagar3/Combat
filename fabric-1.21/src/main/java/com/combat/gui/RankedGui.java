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

        boolean effectsEnabled = ConfigManager.getConfig().topRanksEffectsEnabled;
        ItemStack effectsToggle = new ItemStack(Items.BREWING_STAND);
        effectsToggle.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Top 3 Rank Effects").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        List<Text> effectsLore = new ArrayList<>();
        effectsLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(effectsEnabled ? Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
        effectsLore.add(Text.literal("Click to Toggle Effects").formatted(Formatting.YELLOW, Formatting.ITALIC));
        effectsToggle.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(effectsLore));
        inventory.setStack(22, effectsToggle);

        ItemStack toggle = new ItemStack(ConfigManager.getConfig().rankedSystemEnabled ? Items.LIME_DYE : Items.GRAY_DYE);
        toggle.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ranked System: " + 
            (ConfigManager.getConfig().rankedSystemEnabled ? "ENABLED" : "DISABLED"))
            .formatted(ConfigManager.getConfig().rankedSystemEnabled ? Formatting.GREEN : Formatting.RED, Formatting.BOLD));
        List<Text> toggleLore = new ArrayList<>();
        toggleLore.add(Text.literal("Click to toggle ranked system").formatted(Formatting.YELLOW));
        toggle.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(toggleLore));
        inventory.setStack(26, toggle);

        for (int pos = 1; pos <= 10; pos++) {
            int slot = (pos <= 9) ? (8 + pos) : 23;
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
        } else if (slotId == 23) {
            selectedPos = 10;
        }

        if (selectedPos != -1) {
            new RankPositionBuffsGui(player, selectedPos).open();
        }
    }
}
