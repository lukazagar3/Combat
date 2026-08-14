package com.combat.mixin;

import com.combat.ConfigManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow public DefaultedList<Slot> slots;
    @Shadow public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void preSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (slotId < 0 || slotId >= this.slots.size()) return;

        ScreenHandler handler = (ScreenHandler)(Object)this;
        Slot targetSlot = this.slots.get(slotId);
        boolean isExternalSlot = !(targetSlot.inventory instanceof net.minecraft.entity.player.PlayerInventory);

        ItemStack targetItem = targetSlot.getStack();
        ItemStack carriedItem = this.getCursorStack();

        boolean isWorkstation = handler instanceof net.minecraft.screen.ForgingScreenHandler ||
                                handler instanceof net.minecraft.screen.AnvilScreenHandler ||
                                handler instanceof net.minecraft.screen.CraftingScreenHandler ||
                                handler instanceof net.minecraft.screen.SmithingScreenHandler ||
                                handler instanceof net.minecraft.screen.GrindstoneScreenHandler ||
                                handler instanceof net.minecraft.screen.EnchantmentScreenHandler ||
                                handler instanceof net.minecraft.screen.PlayerScreenHandler;

        // 1. Check taking/crafting items into player inventory (limit checks)
        if (isExternalSlot && !targetItem.isEmpty()) {
            boolean isForgingResult = (handler instanceof net.minecraft.screen.ForgingScreenHandler) && targetSlot.getIndex() == 2;
            if (!isForgingResult) {
                String itemId = Registries.ITEM.getId(targetItem.getItem()).toString();
                if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE ||
                   (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP && carriedItem.isEmpty()) ||
                    actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
                    int allowed = com.combat.CombatMod.getMaxAllowedCount(serverPlayer, itemId);
                    if (allowed <= 0) {
                        serverPlayer.sendMessage(net.minecraft.text.Text.literal("Limit reached for " + itemId.replace("minecraft:", "") + "! Cannot acquire more.").formatted(Formatting.RED), false);
                        ci.cancel();
                        handler.sendContentUpdates();
                        return;
                    }
                    if (targetItem.getCount() > allowed) {
                        if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE) {
                            ItemStack takeStack = targetItem.split(allowed);
                            if (!serverPlayer.getInventory().insertStack(takeStack)) {
                                targetItem.increment(takeStack.getCount());
                            }
                            serverPlayer.sendMessage(net.minecraft.text.Text.literal("Acquired " + (allowed - takeStack.getCount()) + " " + itemId.replace("minecraft:", "") + " (limit reached).").formatted(Formatting.YELLOW), false);
                            ci.cancel();
                            handler.sendContentUpdates();
                            return;
                        } else if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP && carriedItem.isEmpty()) {
                            ItemStack takeStack = targetItem.split(allowed);
                            handler.setCursorStack(takeStack);
                            ci.cancel();
                            handler.sendContentUpdates();
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
                Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(serverPlayer.getUuid());
                boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
                if (!inCombat) {
                    activeInContext = false;
                }
            }

            if (activeInContext) {
                java.util.Map<String, com.combat.CombatConfig.WorldLimitedItem> worldLimits = ConfigManager.getConfig().worldLimits;
                if (!worldLimits.isEmpty()) {
                    if (isExternalSlot && !carriedItem.isEmpty()) {
                        String itemId = Registries.ITEM.getId(carriedItem.getItem()).toString();
                        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
                        if (wli != null && wli.maxCount > 0) {
                            serverPlayer.sendMessage(net.minecraft.text.Text.literal("World-limited items cannot be stored in containers!").formatted(Formatting.RED), false);
                            ci.cancel();
                            handler.sendContentUpdates();
                            return;
                        }
                    } else if (!isExternalSlot && actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE && !targetItem.isEmpty()) {
                        String itemId = Registries.ITEM.getId(targetItem.getItem()).toString();
                        com.combat.CombatConfig.WorldLimitedItem wli = worldLimits.get(itemId);
                        if (wli != null && wli.maxCount > 0) {
                            boolean hasExternal = false;
                            for (Slot s : this.slots) {
                                if (!(s.inventory instanceof net.minecraft.entity.player.PlayerInventory)) {
                                    hasExternal = true;
                                    break;
                                }
                            }
                            if (hasExternal) {
                                serverPlayer.sendMessage(net.minecraft.text.Text.literal("World-limited items cannot be stored in containers!").formatted(Formatting.RED), false);
                                ci.cancel();
                                handler.sendContentUpdates();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "onSlotClick", at = @At("TAIL"))
    private void postSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        com.combat.CombatMod.checkAndEnforceItemLimits(serverPlayer);
    }

    private void enforceInventoryLimit(ServerPlayerEntity player, String itemId, int limit) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        ItemStack offhandStack = player.getInventory().getStack(40);
        if (Registries.ITEM.getId(offhandStack.getItem()).toString().equals(itemId)) {
            total += offhandStack.getCount();
        }

        if (total > limit) {
            int excess = total - limit;
            int toRemove = excess;

            // Remove excess from player inventory
            for (int i = 35; i >= 0; i--) {
                if (excess <= 0) break;
                ItemStack stack = player.getInventory().getStack(i);
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                    int count = stack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().setStack(i, ItemStack.EMPTY);
                    } else {
                        stack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            if (excess > 0) {
                if (Registries.ITEM.getId(offhandStack.getItem()).toString().equals(itemId)) {
                    int count = offhandStack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().setStack(40, ItemStack.EMPTY);
                    } else {
                        offhandStack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            int finalRemoved = toRemove - excess;
            if (finalRemoved <= 0) return;

            ItemStack excessStack = new ItemStack(Registries.ITEM.get(net.minecraft.util.Identifier.of(itemId)), finalRemoved);

            // Try to return to chest container
            ScreenHandler currentHandler = player.currentScreenHandler;
            if (currentHandler instanceof GenericContainerScreenHandler chestHandler) {
                int containerSize = chestHandler.getInventory().size();
                for (int i = 0; i < containerSize; i++) {
                    if (excessStack.isEmpty()) break;
                    Slot slot = chestHandler.getSlot(i);
                    ItemStack slotStack = slot.getStack();
                    if (slotStack.isEmpty()) {
                        slot.setStack(excessStack.copy());
                        excessStack.setCount(0);
                    } else if (ItemStack.areItemsAndComponentsEqual(slotStack, excessStack)) {
                        int max = Math.min(slot.getMaxItemCount(slotStack), slotStack.getMaxCount());
                        int space = max - slotStack.getCount();
                        if (space > 0) {
                            int toAdd = Math.min(space, excessStack.getCount());
                            slotStack.increment(toAdd);
                            excessStack.decrement(toAdd);
                        }
                    }
                }
            }

            // Drop remaining excess on the ground
            if (!excessStack.isEmpty()) {
                player.dropItem(excessStack, false);
            }

            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
