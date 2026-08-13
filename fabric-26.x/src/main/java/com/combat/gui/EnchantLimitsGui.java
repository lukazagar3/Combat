package com.combat.gui;

import com.combat.ConfigManager;
import com.combat.CombatMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public class EnchantLimitsGui extends ChestGui {
    private final int page;
    private final String query;
    private final List<Holder.Reference<Enchantment>> filteredEnchants = new ArrayList<>();

    public EnchantLimitsGui(ServerPlayer player, int page, String query) {
        super(player, query.isEmpty() ? "Choose Enchantments" : "Search: " + query, 6);
        this.page = page;
        this.query = query.trim().toLowerCase();
    }

    @Override
    protected void setupItems() {
        inventory.clearContent();
        filteredEnchants.clear();

        var registry = CombatMod.serverInstance.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var holder : registry.listElements().toList()) {
            String idStr = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            if (query.isEmpty() || idStr.contains(query)) {
                filteredEnchants.add(holder);
            }
        }

        filteredEnchants.sort((a, b) -> {
            String idA = a.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            String idB = b.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            return idA.compareTo(idB);
        });

        int startIdx = page * 5;
        for (int row = 0; row < 5; row++) {
            int idx = startIdx + row;
            if (idx < filteredEnchants.size()) {
                var holder = filteredEnchants.get(idx);
                String enchantId = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
                String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
                String name = capitalize(path);
                int maxLevel = holder.value().getMaxLevel();
                Integer limit = ConfigManager.getConfig().enchantLimits.get(enchantId);

                // Col 0: Enchantment Book
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("ID: " + enchantId).withStyle(ChatFormatting.DARK_GRAY));
                if (limit == null) {
                    lore.add(Component.literal("Current Limit: Vanilla Max (" + maxLevel + ")").withStyle(ChatFormatting.GRAY));
                } else if (limit == 0) {
                    lore.add(Component.literal("Current Limit: BANNED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                } else {
                    lore.add(Component.literal("Current Limit: Level " + getRoman(limit)).withStyle(ChatFormatting.GREEN));
                }
                lore.add(Component.literal(""));
                lore.add(Component.literal("Click to BAN completely (all levels)").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
                book.set(DataComponents.LORE, new ItemLore(lore));
                inventory.setItem(row * 9, book);

                // Cols 1 to maxLevel: level buttons
                for (int level = 1; level <= 5; level++) {
                    int slot = row * 9 + level;
                    if (level <= maxLevel) {
                        ItemStack pane;
                        boolean isSelected = (limit != null && limit == level) || (limit == null && level == maxLevel);
                        boolean isBanned = (limit != null && limit == 0);

                        if (isBanned) {
                            pane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:red_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                        } else if (isSelected) {
                            pane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:green_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                        } else {
                            pane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:light_gray_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                        }

                        pane.set(DataComponents.CUSTOM_NAME, Component.literal(getRoman(level)).withStyle(
                            isBanned ? ChatFormatting.RED : (isSelected ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                            ChatFormatting.BOLD
                        ));
                        List<Component> paneLore = new ArrayList<>();
                        paneLore.add(Component.literal("Set Max Level to " + getRoman(level)).withStyle(ChatFormatting.GRAY));
                        pane.set(DataComponents.LORE, new ItemLore(paneLore));
                        inventory.setItem(slot, pane);
                    } else {
                        // Empty slot border
                        ItemStack borderPane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:black_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                        borderPane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
                        inventory.setItem(slot, borderPane);
                    }
                }

                // Fill rest of row with black panes
                for (int c = maxLevel + 1; c < 9; c++) {
                    ItemStack borderPane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:black_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                    borderPane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
                    inventory.setItem(row * 9 + c, borderPane);
                }
            } else {
                // Empty row
                ItemStack borderPane = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:black_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
                borderPane.set(DataComponents.CUSTOM_NAME, Component.literal(""));
                for (int c = 0; c < 9; c++) {
                    inventory.setItem(row * 9 + c, borderPane);
                }
            }
        }

        ItemStack border = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse("minecraft:gray_stained_glass_pane")).map(Holder::value).orElse(Items.AIR));
        border.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        inventory.setItem(46, border);
        inventory.setItem(47, border);
        inventory.setItem(51, border);
        inventory.setItem(52, border);

        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page").withStyle(ChatFormatting.YELLOW));
            inventory.setItem(45, prev);
        } else {
            inventory.setItem(45, border);
        }

        if ((page + 1) * 5 < filteredEnchants.size()) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page").withStyle(ChatFormatting.YELLOW));
            inventory.setItem(53, next);
        } else {
            inventory.setItem(53, border);
        }

        ItemStack back = new ItemStack(Items.BARRIER);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back to Menu").withStyle(ChatFormatting.RED));
        inventory.setItem(49, back);

        ItemStack searchTag = new ItemStack(Items.NAME_TAG);
        searchTag.set(DataComponents.CUSTOM_NAME, Component.literal("Search Enchantment Name").withStyle(ChatFormatting.GREEN));
        List<Component> searchLore = new ArrayList<>();
        if (!query.isEmpty()) {
            searchLore.add(Component.literal("Current Filter: " + query).withStyle(ChatFormatting.GRAY));
        }
        searchLore.add(Component.literal("Click to enter search query").withStyle(ChatFormatting.YELLOW));
        searchTag.set(DataComponents.LORE, new ItemLore(searchLore));
        inventory.setItem(48, searchTag);

        ItemStack clearFilter = new ItemStack(Items.MILK_BUCKET);
        clearFilter.set(DataComponents.CUSTOM_NAME, Component.literal("Clear Filter").withStyle(ChatFormatting.AQUA));
        List<Component> clearLore = new ArrayList<>();
        clearLore.add(Component.literal("Click to clear search filters").withStyle(ChatFormatting.YELLOW));
        clearFilter.set(DataComponents.LORE, new ItemLore(clearLore));
        inventory.setItem(50, clearFilter);
    }

    @Override
    protected void handleSlotClick(int slotId, int clickData, ContainerInput actionType) {
        if (slotId >= 0 && slotId < 45) {
            int row = slotId / 9;
            int col = slotId % 9;
            int startIdx = page * 5;
            int idx = startIdx + row;

            if (idx < filteredEnchants.size()) {
                var holder = filteredEnchants.get(idx);
                String enchantId = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
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
