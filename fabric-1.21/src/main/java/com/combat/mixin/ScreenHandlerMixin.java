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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow public abstract List<Slot> getSlots();
    @Shadow public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void preSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        ScreenHandler handler = (ScreenHandler)(Object)this;
        boolean isEnderChest = false;
        for (Slot slot : this.getSlots()) {
            if (slot.inventory instanceof net.minecraft.inventory.EnderChestInventory) {
                isEnderChest = true;
                break;
            }
        }

        if (isEnderChest) {
            java.util.Set<String> worldLimitedItems = ConfigManager.getConfig().worldLimits.keySet();
            boolean attemptingToPutInEC = false;
            ItemStack stackToMove = ItemStack.EMPTY;

            if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE) {
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (!(slot.inventory instanceof net.minecraft.inventory.EnderChestInventory)) {
                        stackToMove = slot.getStack();
                        attemptingToPutInEC = true;
                    }
                }
            } else if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (slot.inventory instanceof net.minecraft.inventory.EnderChestInventory) {
                        stackToMove = this.getCursorStack();
                        attemptingToPutInEC = true;
                    }
                }
            } else if (actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (slot.inventory instanceof net.minecraft.inventory.EnderChestInventory) {
                        int hotbarSlot = clickData;
                        stackToMove = serverPlayer.getInventory().getStack(hotbarSlot);
                        attemptingToPutInEC = true;
                    }
                }
            }

            if (attemptingToPutInEC && !stackToMove.isEmpty()) {
                String itemId = Registries.ITEM.getId(stackToMove.getItem()).toString();
                if (worldLimitedItems.contains(itemId)) {
                    serverPlayer.sendMessage(net.minecraft.text.Text.literal("You cannot put world-limited items in an Ender Chest!").formatted(net.minecraft.util.Formatting.RED), false);
                    ci.cancel();
                    handler.sendContentUpdates();
                }
            }
        }
    }

    @Inject(method = "onSlotClick", at = @At("TAIL"))
    private void postSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // Enforce inventory limit for all configured items
        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().itemLimits.entrySet()) {
            enforceInventoryLimit(serverPlayer, entry.getKey(), entry.getValue());
        }
    }

    private void enforceInventoryLimit(ServerPlayerEntity player, String itemId, int limit) {
        int total = 0;
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }

        if (total > limit) {
            int excess = total - limit;
            int toRemove = excess;

            // Remove excess from player inventory
            for (int i = player.getInventory().main.size() - 1; i >= 0; i--) {
                if (excess <= 0) break;
                ItemStack stack = player.getInventory().main.get(i);
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                    int count = stack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().main.set(i, ItemStack.EMPTY);
                    } else {
                        stack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            if (excess > 0) {
                for (int i = 0; i < player.getInventory().offHand.size(); i++) {
                    if (excess <= 0) break;
                    ItemStack stack = player.getInventory().offHand.get(i);
                    if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                        int count = stack.getCount();
                        if (count <= excess) {
                            excess -= count;
                            player.getInventory().offHand.set(i, ItemStack.EMPTY);
                        } else {
                            stack.setCount(count - excess);
                            excess = 0;
                        }
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
