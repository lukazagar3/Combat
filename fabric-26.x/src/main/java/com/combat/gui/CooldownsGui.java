package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;

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

        ItemStack border = new ItemStack(Items.GLASS_PANE);
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

            // Get the item, trying netherite_spear first for spear icon
            net.minecraft.world.item.Item resolvedItem;
            if ("minecraft:spear".equals(itemId)) {
                resolvedItem = BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:netherite_spear"))
                    .map(r -> r.value())
                    .filter(it -> it != net.minecraft.world.item.Items.AIR)
                    .orElseGet(() -> BuiltInRegistries.ITEM.get(Identifier.parse("minecraft:spear"))
                        .map(r -> r.value()).orElse(net.minecraft.world.item.Items.AIR));
            } else {
                resolvedItem = BuiltInRegistries.ITEM.get(Identifier.parse(itemId))
                    .map(r -> r.value()).orElse(net.minecraft.world.item.Items.AIR);
            }

            ItemStack itemStack;
            if (resolvedItem == net.minecraft.world.item.Items.AIR) {
                // Fallback icon if spear isn't in registry
                itemStack = switch (itemId) {
                    case "minecraft:spear" -> new ItemStack(Items.NETHERITE_SWORD);
                    default -> new ItemStack(Items.STICK);
                };
            } else {
                itemStack = new ItemStack(resolvedItem);
            }

            // Friendly display names
            String displayName = switch (itemId) {
                case "minecraft:ender_pearl" -> "Ender Pearl";
                case "minecraft:mace" -> "Mace";
                case "minecraft:trident" -> "Trident";
                case "minecraft:spear" -> "Spear (Lunge)";
                default -> itemId.replace("minecraft:", "");
            };

            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(Component.literal("Cooldown: " + cooldown + "s").withStyle(ChatFormatting.GRAY));
            loreLines.add(Component.literal("Click to Change").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            itemStack.set(DataComponents.LORE, new ItemLore(loreLines));

            inventory.setItem(10 + i, itemStack);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
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
