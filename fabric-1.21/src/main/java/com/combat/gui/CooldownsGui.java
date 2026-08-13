package com.combat.gui;

import com.combat.ConfigManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CooldownsGui extends ChestGui {
    private final List<String> targetedItems = new ArrayList<>();

    public CooldownsGui(ServerPlayerEntity player) {
        super(player, "Targeted Item Cooldowns", 3);
        // Cooldown items restriction
        targetedItems.add("minecraft:ender_pearl");
        targetedItems.add("minecraft:mace");
        targetedItems.add("minecraft:trident");
        targetedItems.add("minecraft:spear"); // Added in 1.21.11
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

        for (int i = 0; i < targetedItems.size(); i++) {
            String itemId = targetedItems.get(i);
            double cooldown = ConfigManager.getConfig().itemCooldowns.getOrDefault(itemId, 0.0);

            net.minecraft.item.Item resolvedItem;
            try {
                if ("minecraft:spear".equals(itemId)) {
                    resolvedItem = Registries.ITEM.get(Identifier.of("minecraft:netherite_spear"));
                    if (resolvedItem == Items.AIR) {
                        resolvedItem = Registries.ITEM.get(Identifier.of("minecraft:spear"));
                    }
                } else {
                    resolvedItem = Registries.ITEM.get(Identifier.of(itemId));
                }
            } catch (Exception e) {
                resolvedItem = Items.AIR;
            }

            ItemStack itemStack;
            if (resolvedItem == Items.AIR) {
                itemStack = switch (itemId) {
                    case "minecraft:spear" -> new ItemStack(Items.NETHERITE_SWORD);
                    default -> new ItemStack(Items.STICK);
                };
            } else {
                itemStack = new ItemStack(resolvedItem);
            }

            String displayName = switch (itemId) {
                case "minecraft:ender_pearl" -> "Ender Pearl";
                case "minecraft:mace" -> "Mace";
                case "minecraft:trident" -> "Trident";
                case "minecraft:spear" -> "Spear (Lunge)";
                default -> itemId.replace("minecraft:", "");
            };

            itemStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(displayName).formatted(Formatting.GOLD, Formatting.BOLD));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Cooldown: " + cooldown + "s").formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to Change").formatted(Formatting.YELLOW, Formatting.ITALIC));
            itemStack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

            inventory.setStack(10 + i, itemStack);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
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
                            player.sendMessage(Text.literal("Cooldown cannot be negative!").formatted(Formatting.RED), false);
                        } else {
                            ConfigManager.getConfig().itemCooldowns.put(itemId, seconds);
                            ConfigManager.save();
                            player.sendMessage(Text.literal("Cooldown for " + itemId + " set to " + seconds + "s").formatted(Formatting.GREEN), false);
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Text.literal("Invalid seconds!").formatted(Formatting.RED), false);
                    }
                    new CooldownsGui(player).open();
                }
            }.open();
        }
    }
}
