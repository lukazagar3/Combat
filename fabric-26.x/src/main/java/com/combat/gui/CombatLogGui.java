package com.combat.gui;

import com.combat.ConfigManager;
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

public class CombatLogGui extends ChestGui {

    public CombatLogGui(ServerPlayer player) {
        super(player, "Combat & Immunity Settings", 3);
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

        ItemStack logTimer = new ItemStack(Items.REDSTONE);
        logTimer.set(DataComponents.CUSTOM_NAME, Component.literal("Combat Tag Timer").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        List<Component> logLore = new ArrayList<>();
        logLore.add(Component.literal("Current: " + ConfigManager.getConfig().combatLogSeconds + "s").withStyle(ChatFormatting.GRAY));
        logLore.add(Component.literal("Click to Edit").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        logTimer.set(DataComponents.LORE, new ItemLore(logLore));
        inventory.setItem(10, logTimer);

        boolean ecAllowed = ConfigManager.getConfig().allowEnderchestInCombat;
        ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
        enderChest.set(DataComponents.CUSTOM_NAME, Component.literal("Ender Chest Access").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        List<Component> ecLore = new ArrayList<>();
        ecLore.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
            .append(ecAllowed ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        ecLore.add(Component.literal("Click to Toggle").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        enderChest.set(DataComponents.LORE, new ItemLore(ecLore));
        inventory.setItem(12, enderChest);

        ItemStack immunityTimer = new ItemStack(Items.GOLDEN_APPLE);
        immunityTimer.set(DataComponents.CUSTOM_NAME, Component.literal("Immunity Timer").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        List<Component> immunityLore = new ArrayList<>();
        immunityLore.add(Component.literal("Current: " + ConfigManager.getConfig().immunitySeconds + "s").withStyle(ChatFormatting.GRAY));
        immunityLore.add(Component.literal("Click to Edit").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        immunityTimer.set(DataComponents.LORE, new ItemLore(immunityLore));
        inventory.setItem(14, immunityTimer);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
        } else if (slotId == 10) {
            new AnvilInputGui(player, "Combat Tag Duration (sec)", String.valueOf(ConfigManager.getConfig().combatLogSeconds)) {
                @Override
                protected void handleInput(String input) {
                    try {
                        int seconds = Integer.parseInt(input.trim());
                        if (seconds < 0) {
                            player.sendSystemMessage(Component.literal("Cannot be negative!").withStyle(ChatFormatting.RED));
                        } else {
                            ConfigManager.getConfig().combatLogSeconds = seconds;
                            ConfigManager.save();
                        }
                    } catch (NumberFormatException e) {
                        player.sendSystemMessage(Component.literal("Invalid seconds!").withStyle(ChatFormatting.RED));
                    }
                    new CombatLogGui(player).open();
                }
            }.open();
        } else if (slotId == 12) {
            ConfigManager.getConfig().allowEnderchestInCombat = !ConfigManager.getConfig().allowEnderchestInCombat;
            ConfigManager.save();
            setupItems();
        } else if (slotId == 14) {
            new AnvilInputGui(player, "Immunity Duration (sec)", String.valueOf(ConfigManager.getConfig().immunitySeconds)) {
                @Override
                protected void handleInput(String input) {
                    try {
                        int seconds = Integer.parseInt(input.trim());
                        if (seconds < 0) {
                            player.sendSystemMessage(Component.literal("Cannot be negative!").withStyle(ChatFormatting.RED));
                        } else {
                            ConfigManager.getConfig().immunitySeconds = seconds;
                            ConfigManager.save();
                        }
                    } catch (NumberFormatException e) {
                        player.sendSystemMessage(Component.literal("Invalid seconds!").withStyle(ChatFormatting.RED));
                    }
                    new CombatLogGui(player).open();
                }
            }.open();
        }
    }
}
