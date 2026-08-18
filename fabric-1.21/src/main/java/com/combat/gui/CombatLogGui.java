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
        super(player, "Combat Settings", 3);
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

        ItemStack logTimer = new ItemStack(Items.REDSTONE);
        logTimer.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Combat Tag Timer").formatted(Formatting.RED, Formatting.BOLD));
        List<Text> logLore = new ArrayList<>();
        logLore.add(Text.literal("Current: " + ConfigManager.getConfig().combatLogSeconds + "s").formatted(Formatting.GRAY));
        logLore.add(Text.literal("Click to Edit").formatted(Formatting.YELLOW, Formatting.ITALIC));
        logTimer.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(logLore));
        inventory.setStack(10, logTimer);

        boolean ecAllowed = ConfigManager.getConfig().allowEnderchestInCombat;
        ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
        enderChest.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ender Chest Access").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        List<Text> ecLore = new ArrayList<>();
        ecLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(ecAllowed ? Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
        ecLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        enderChest.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(ecLore));
        inventory.setStack(12, enderChest);

        boolean elytraAllowed = ConfigManager.getConfig().allowElytraInCombat;
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Equip Elytra in Combat").formatted(Formatting.AQUA, Formatting.BOLD));
        List<Text> elytraLore = new ArrayList<>();
        elytraLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(elytraAllowed ? Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
        elytraLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        elytra.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(elytraLore));
        inventory.setStack(14, elytra);

        boolean fireworkAllowed = ConfigManager.getConfig().allowFireworksInCombat;
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        firework.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Firework Rockets in Combat").formatted(Formatting.DARK_GREEN, Formatting.BOLD));
        List<Text> fireworkLore = new ArrayList<>();
        fireworkLore.add(Text.literal("Status: ").formatted(Formatting.GRAY)
            .append(fireworkAllowed ? Text.literal("ENABLED").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("DISABLED").formatted(Formatting.RED, Formatting.BOLD)));
        fireworkLore.add(Text.literal("Click to Toggle").formatted(Formatting.YELLOW, Formatting.ITALIC));
        firework.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(fireworkLore));
        inventory.setStack(16, firework);
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
            setupItems();
        } else if (slotId == 14) {
            ConfigManager.getConfig().allowElytraInCombat = !ConfigManager.getConfig().allowElytraInCombat;
            ConfigManager.save();
            setupItems();
        } else if (slotId == 16) {
            ConfigManager.getConfig().allowFireworksInCombat = !ConfigManager.getConfig().allowFireworksInCombat;
            ConfigManager.save();
            setupItems();
        }
    }
}
