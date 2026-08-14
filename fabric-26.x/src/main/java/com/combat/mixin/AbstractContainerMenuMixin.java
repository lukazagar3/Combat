package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.core.NonNullList;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void preSlotClick(int slotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (slotId < 0 || slotId >= this.slots.size()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        Slot targetSlot = this.slots.get(slotId);
        boolean isExternalSlot = !(targetSlot.container instanceof net.minecraft.world.entity.player.Inventory);
        String actionName = actionType.name();

        ItemStack targetItem = targetSlot.getItem();
        ItemStack carriedItem = this.getCarried();

        boolean isWorkstation = menu instanceof net.minecraft.world.inventory.ItemCombinerMenu ||
                                menu instanceof net.minecraft.world.inventory.AnvilMenu ||
                                menu instanceof net.minecraft.world.inventory.CraftingMenu ||
                                menu instanceof net.minecraft.world.inventory.SmithingMenu ||
                                menu instanceof net.minecraft.world.inventory.GrindstoneMenu ||
                                menu instanceof net.minecraft.world.inventory.EnchantmentMenu ||
                                menu instanceof net.minecraft.world.inventory.InventoryMenu;

        // 1. Check taking/crafting items into player inventory (limit checks)
        if (isExternalSlot && !targetItem.isEmpty()) {
            boolean isCombinerResult = (menu instanceof net.minecraft.world.inventory.ItemCombinerMenu) && targetSlot.index == 2;
            if (!isCombinerResult) {
                String itemId = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                if (actionName.equals("QUICK_MOVE") || (actionName.equals("PICKUP") && carriedItem.isEmpty()) || actionName.equals("SWAP")) {
                    int allowed = com.combat.CombatMod.getMaxAllowedCount(serverPlayer, itemId);
                    if (allowed <= 0) {
                        serverPlayer.sendSystemMessage(Component.literal("Limit reached for " + itemId.replace("minecraft:", "") + "! Cannot acquire more.").withStyle(ChatFormatting.RED));
                        ci.cancel();
                        menu.broadcastChanges();
                        return;
                    }
                    if (targetItem.getCount() > allowed) {
                        if (actionName.equals("QUICK_MOVE")) {
                            ItemStack takeStack = targetItem.split(allowed);
                            if (!serverPlayer.getInventory().add(takeStack)) {
                                targetItem.grow(takeStack.getCount());
                            }
                            serverPlayer.sendSystemMessage(Component.literal("Acquired " + (allowed - takeStack.getCount()) + " " + itemId.replace("minecraft:", "") + " (limit reached).").withStyle(ChatFormatting.YELLOW));
                            ci.cancel();
                            menu.broadcastChanges();
                            return;
                        } else if (actionName.equals("PICKUP") && carriedItem.isEmpty()) {
                            ItemStack takeStack = targetItem.split(allowed);
                            menu.setCarried(takeStack);
                            ci.cancel();
                            menu.broadcastChanges();
                            return;
                        }
                    }
                }
            }
        }

        // 2. Check placing world-limited items into external STORAGE containers
        if (!isWorkstation) {
            boolean activeInContext = true;
            if (ConfigManager.getConfig().limitsOnlyInCombat) {
                Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(serverPlayer.getUUID());
                boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
                if (!inCombat) {
                    activeInContext = false;
                }
            }

            if (activeInContext) {
                java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits = ConfigManager.getConfig().worldLimits;
                if (!worldLimits.isEmpty()) {
                    if (isExternalSlot && !carriedItem.isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(carriedItem.getItem()).toString();
                        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
                        if (wli != null && wli.maxCount > 0) {
                            serverPlayer.sendSystemMessage(Component.literal("World-limited items cannot be stored in containers!").withStyle(ChatFormatting.RED));
                            ci.cancel();
                            menu.broadcastChanges();
                            return;
                        }
                    } else if (!isExternalSlot && actionName.equals("QUICK_MOVE") && !targetItem.isEmpty()) {
                        String itemId = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
                        if (wli != null && wli.maxCount > 0) {
                            boolean hasExternal = false;
                            for (Slot s : this.slots) {
                                if (!(s.container instanceof net.minecraft.world.entity.player.Inventory)) {
                                    hasExternal = true;
                                    break;
                                }
                            }
                            if (hasExternal) {
                                serverPlayer.sendSystemMessage(Component.literal("World-limited items cannot be stored in containers!").withStyle(ChatFormatting.RED));
                                ci.cancel();
                                menu.broadcastChanges();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"))
    private void postSlotClick(int slotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        com.combat.CombatMod.checkAndEnforceItemLimits(serverPlayer);
    }

    private void enforceInventoryLimit(ServerPlayer player, String itemId, int limit) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        ItemStack offhandStack = player.getInventory().getItem(40);
        if (BuiltInRegistries.ITEM.getKey(offhandStack.getItem()).toString().equals(itemId)) {
            total += offhandStack.getCount();
        }

        if (total > limit) {
            int excess = total - limit;
            int toRemove = excess;

            // Remove excess from player inventory
            for (int i = 35; i >= 0; i--) {
                if (excess <= 0) break;
                ItemStack stack = player.getInventory().getItem(i);
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                    int count = stack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    } else {
                        stack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            if (excess > 0) {
                if (BuiltInRegistries.ITEM.getKey(offhandStack.getItem()).toString().equals(itemId)) {
                    int count = offhandStack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().setItem(40, ItemStack.EMPTY);
                    } else {
                        offhandStack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            int finalRemoved = toRemove - excess;
            if (finalRemoved <= 0) return;

            ItemStack excessStack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).map(r -> r.value()).orElse(net.minecraft.world.item.Items.AIR), finalRemoved);

            AbstractContainerMenu currentHandler = player.containerMenu;
            if (currentHandler instanceof ChestMenu chestHandler) {
                int containerSize = chestHandler.getContainer().getContainerSize();
                for (int i = 0; i < containerSize; i++) {
                    if (excessStack.isEmpty()) break;
                    Slot slot = chestHandler.getSlot(i);
                    ItemStack slotStack = slot.getItem();
                    if (slotStack.isEmpty()) {
                        slot.set(excessStack.copy());
                        excessStack.setCount(0);
                    } else if (ItemStack.isSameItemSameComponents(slotStack, excessStack)) {
                        int max = Math.min(slot.getMaxStackSize(slotStack), slotStack.getMaxStackSize());
                        int space = max - slotStack.getCount();
                        if (space > 0) {
                            int toAdd = Math.min(space, excessStack.getCount());
                            slotStack.grow(toAdd);
                            excessStack.shrink(toAdd);
                        }
                    }
                }
            }

            if (!excessStack.isEmpty()) {
                player.drop(excessStack, false);
            }

            player.containerMenu.broadcastChanges();
        }
    }
}
