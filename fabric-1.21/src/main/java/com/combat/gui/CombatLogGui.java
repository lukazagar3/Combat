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

public class CombatLogGui extends ChestGui {

    public CombatLogGui(ServerPlayerEntity player) {
        super(player, "Combat & Immunity Settings", 3);
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

        // Back
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.YELLOW));
        inventory.setStack(18, back);

        // 1. Combat Log Timer
        ItemStack logTimer = new ItemStack(Items.REDSTONE);
        logTimer.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Combat Tag Timer").formatted(Formatting.RED, Formatting.BOLD));
        List<Text> logLore = new ArrayList<>();
        logLore.add(Text.literal("Current: " + ConfigManager.getConfig().combatLogSeconds + "s").formatted(Formatting.GRAY));
        logLore.add(Text.literal("Click to Edit").formatted(Formatting.YELLOW, Formatting.ITALIC));
        logTimer.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(logLore));
        inventory.setStack(10, logTimer);

        // 2. Ender Chest Access in Combat
        boolean ecAllowed = ConfigManager.getConfig().allowEnderchestInCombat;
        ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
        enderChest.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ender Chest Access").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        List<Text> ecLore = new ArrayList<>();
        ecLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(ecAllowed ? Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
        ecLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        enderChest.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(ecLore));
        inventory.setStack(12, enderChest);

        // 3. Post-respawn Immunity Timer
        ItemStack immunityTimer = new ItemStack(Items.GOLDEN_APPLE);
        immunityTimer.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Immunity Timer").formatted(Formatting.GOLD, Formatting.BOLD));
        List<Text> immunityLore = new ArrayList<>();
        immunityLore.add(Text.literal("Current: " + ConfigManager.getConfig().immunitySeconds + "s").formatted(Formatting.GRAY));
        immunityLore.add(Text.literal("Click to Edit").formatted(Formatting.YELLOW, Formatting.ITALIC));
        immunityTimer.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(immunityLore));
        inventory.setStack(14, immunityTimer);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
        } else if (slotId == 10) {
            new AnvilInputGui(player, "Combat Tag Duration (sec)", String.valueOf(ConfigManager.getConfig().combatLogSeconds)) {
                @Override
                protected void handleInput(String input) {
                    try {
                        int seconds = Integer.parseInt(input.trim());
                        if (seconds < 0) {
                            player.sendMessage(Text.literal("Cannot be negative!").formatted(Formatting.RED), false);
                        } else {
                            ConfigManager.getConfig().combatLogSeconds = seconds;
                            ConfigManager.save();
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Text.literal("Invalid seconds!").formatted(Formatting.RED), false);
                    }
                    new CombatLogGui(player).open();
                }
            }.open();
        } else if (slotId == 12) {
            ConfigManager.getConfig().allowEnderchestInCombat = !ConfigManager.getConfig().allowEnderchestInCombat;
            ConfigManager.save();
            setupItems(); // Refresh GUI
        } else if (slotId == 14) {
            new AnvilInputGui(player, "Immunity Duration (sec)", String.valueOf(ConfigManager.getConfig().immunitySeconds)) {
                @Override
                protected void handleInput(String input) {
                    try {
                        int seconds = Integer.parseInt(input.trim());
                        if (seconds < 0) {
                            player.sendMessage(Text.literal("Cannot be negative!").formatted(Formatting.RED), false);
                        } else {
                            ConfigManager.getConfig().immunitySeconds = seconds;
                            ConfigManager.save();
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Text.literal("Invalid seconds!").formatted(Formatting.RED), false);
                    }
                    new CombatLogGui(player).open();
                }
            }.open();
        }
    }
}
