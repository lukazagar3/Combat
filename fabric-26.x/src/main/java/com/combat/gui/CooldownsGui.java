package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.LoreComponent;

import java.util.ArrayList;
import java.util.List;

public class CooldownsGui extends ChestGui {
    private final List<String> targetedItems = new ArrayList<>();

    public CooldownsGui(ServerPlayer player) {
        super(player, "Targeted Item Cooldowns", 3);
        targetedItems.add("minecraft:ender_pearl");
        targetedItems.add("minecraft:mace");
        targetedItems.add("minecraft:trident");
        targetedItems.add("minecraft:spear");
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

        for (int i = 0; i < targetedItems.size(); i++) {
            String itemId = targetedItems.get(i);
            double cooldown = ConfigManager.getConfig().itemCooldowns.getOrDefault(itemId, 0.0);

            ItemStack itemStack;
            try {
                itemStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
            } catch (Exception e) {
                itemStack = new ItemStack(Items.TRIDENT);
            }
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(itemId).withStyle(ChatFormatting.GOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Cooldown: " + cooldown + "s").withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Change").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            itemStack.set(DataComponents.LORE, new LoreComponent(loreLines));

            inventory.setItem(10 + i, itemStack);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ClickType actionType) {
        if (slotId == 18) {
            new CombatMenuGui(player).open();
        } else if (slotId >= 10 && slotId < 10 + targetedItems.size()) {
            String itemId = targetedItems.get(slotId - 10);
            double currentCooldown = ConfigManager.getConfig().itemCooldowns.getOrDefault(itemId, 0.0);

            new AnvilInputGui(player, "Set Cooldown (seconds)", String.valueOf(currentCooldown)) {
                @Override
                protected void handleInput(String inputSeconds) {
                    try {
                        double seconds = Double.parseDouble(inputSeconds.trim());
                        if (seconds < 0) {
                            player.sendSystemMessage(Component.literal("Cooldown cannot be negative!").withStyle(ChatFormatting.RED));
                        } else {
                            ConfigManager.getConfig().itemCooldowns.put(itemId, seconds);
                            ConfigManager.save();
                            player.sendSystemMessage(Component.literal("Cooldown for " + itemId + " set to " + seconds + "s").withStyle(ChatFormatting.GREEN));
                        }
                    } catch (NumberFormatException e) {
                        player.sendSystemMessage(Component.literal("Invalid seconds!").withStyle(ChatFormatting.RED));
                    }
                    new CooldownsGui(player).open();
                }
            }.open();
        }
    }
}
