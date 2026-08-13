package com.combat.gui;

import com.combat.ConfigManager;
import com.combat.CombatMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

public class EnchantLimitsGui extends ChestGui {
    private final int page;
    private final String query;
    private final List<RegistryEntry.Reference<Enchantment>> filteredEnchants = new ArrayList<>();

    public EnchantLimitsGui(ServerPlayerEntity player, int page, String query) {
        super(player, query.isEmpty() ? "Choose Enchantments" : "Search: " + query, 6);
        this.page = page;
        this.query = query.trim().toLowerCase();
    }

    @Override
    protected void setupItems() {
        inventory.clear();
        filteredEnchants.clear();

        var registry = CombatMod.serverInstance.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElseThrow();
        for (var holder : registry.streamEntries().toList()) {
            String idStr = holder.registryKey().getValue().toString();
            if (query.isEmpty() || idStr.contains(query)) {
                filteredEnchants.add(holder);
            }
        }

        filteredEnchants.sort((a, b) -> {
            String idA = a.registryKey().getValue().toString();
            String idB = b.registryKey().getValue().toString();
            return idA.compareTo(idB);
        });

        int startIdx = page * 5;
        for (int row = 0; row < 5; row++) {
            int idx = startIdx + row;
            if (idx < filteredEnchants.size()) {
                var holder = filteredEnchants.get(idx);
                String enchantId = holder.registryKey().getValue().toString();
                String path = holder.registryKey().getValue().getPath();
                String name = capitalize(path);
                int maxLevel = holder.value().getMaxLevel();
                Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);

                // Col 0: Enchantment Book
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD));
                List<Text> lore = new ArrayList<>();
                lore.add(Text.literal("ID: " + enchantId).formatted(Formatting.DARK_GRAY));
                if (limit == null) {
                    lore.add(Text.literal("Current Limit: Vanilla Max (" + maxLevel + ")").formatted(Formatting.GRAY));
                } else if (limit == 0) {
                    lore.add(Text.literal("Current Limit: BANNED").formatted(Formatting.RED, Formatting.BOLD));
                } else {
                    lore.add(Text.literal("Current Limit: Level " + getRoman(limit)).formatted(Formatting.GREEN));
                }
                lore.add(Text.literal(""));
                lore.add(Text.literal("Click to BAN completely (all levels)").formatted(Formatting.YELLOW, Formatting.ITALIC));
                book.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                inventory.setStack(row * 9, book);

                // Cols 1 to maxLevel: level buttons
                for (int level = 1; level <= 5; level++) {
                    int slot = row * 9 + level;
                    if (level <= maxLevel) {
                        ItemStack pane;
                        boolean isSelected = (limit != null && limit == level) || (limit == null && level == maxLevel);
                        boolean isBanned = (limit != null && limit == 0);

                        if (isBanned) {
                            pane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                        } else if (isSelected) {
                            pane = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
                        } else {
                            pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                        }

                        pane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(getRoman(level)).formatted(
                            isBanned ? Formatting.RED : (isSelected ? Formatting.GREEN : Formatting.GRAY),
                            Formatting.BOLD
                        ));
                        List<Text> paneLore = new ArrayList<>();
                        paneLore.add(Text.literal("Set Max Level to " + getRoman(level)).formatted(Formatting.GRAY));
                        pane.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(paneLore));
                        inventory.setStack(slot, pane);
                    } else {
                        // Empty slot border
                        ItemStack borderPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                        borderPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
                        inventory.setStack(slot, borderPane);
                    }
                }

                // Fill rest of row with black panes
                for (int c = maxLevel + 1; c < 9; c++) {
                    ItemStack borderPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                    borderPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
                    inventory.setStack(row * 9 + c, borderPane);
                }
            } else {
                // Empty row
                ItemStack borderPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                borderPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
                for (int c = 0; c < 9; c++) {
                    inventory.setStack(row * 9 + c, borderPane);
                }
            }
        }

        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(""));
        inventory.setStack(46, border);
        inventory.setStack(47, border);
        inventory.setStack(51, border);
        inventory.setStack(52, border);

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Previous Page").formatted(Formatting.YELLOW));
            inventory.setStack(45, prev);
        } else {
            inventory.setStack(45, border);
        }

        if ((page + 1) * 5 < filteredEnchants.size()) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Next Page").formatted(Formatting.YELLOW));
            inventory.setStack(53, next);
        } else {
            inventory.setStack(53, border);
        }

        ItemStack back = new ItemStack(Items.BARRIER);
        back.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Back to Main Menu").formatted(Formatting.RED));
        inventory.setStack(49, back);

        ItemStack search = new ItemStack(Items.NAME_TAG);
        search.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Search Enchantments").formatted(Formatting.GREEN));
        if (!query.isEmpty()) {
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Current Search: " + query).formatted(Formatting.GRAY));
            search.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
        }
        inventory.setStack(48, search);

        if (!query.isEmpty()) {
            ItemStack clear = new ItemStack(Items.MILK_BUCKET);
            clear.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Clear Search").formatted(Formatting.YELLOW));
            inventory.setStack(50, clear);
        } else {
            inventory.setStack(50, border);
        }
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, SlotActionType actionType) {
        if (slotId >= 0 && slotId < 45) {
            int row = slotId / 9;
            int col = slotId % 9;
            int startIdx = page * 5;
            int idx = startIdx + row;

            if (idx < filteredEnchants.size()) {
                var holder = filteredEnchants.get(idx);
                String enchantId = holder.registryKey().getValue().toString();
                int maxLevel = holder.value().getMaxLevel();

                if (col == 0) {
                    // Clicked book -> Toggle Ban (set limit to 0 or remove limit if already 0)
                    Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);
                    if (limit != null && limit == 0) {
                        ConfigManager.getConfig().enchantLimits.remove(enchantId);
                    } else {
                        ConfigManager.getConfig().enchantLimits.put(enchantId, 0);
                    }
                    ConfigManager.save();
                    setupItems();
                } else if (col >= 1 && col <= maxLevel) {
                    // Clicked a level button
                    ConfigManager.getConfig().enchantLimits.put(enchantId, col);
                    ConfigManager.save();
                    setupItems();
                }
            }
        } else if (slotId == 45) {
            if (page > 0) {
                new EnchantLimitsGui(player, page - 1, query).open();
            }
        } else if (slotId == 53) {
            if ((page + 1) * 5 < filteredEnchants.size()) {
                new EnchantLimitsGui(player, page + 1, query).open();
            }
        } else if (slotId == 49) {
            new CombatMenuGui(player).open();
        } else if (slotId == 48) {
            new AnvilInputGui(player, "Search Enchant Name", "") {
                @Override
                protected void handleInput(String searchInput) {
                    new EnchantLimitsGui(player, 0, searchInput).open();
                }
            }.open();
        } else if (slotId == 50) {
            if (!query.isEmpty()) {
                new EnchantLimitsGui(player, 0, "").open();
            }
        }
    }

    private String capitalize(String s) {
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String getRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }
}
