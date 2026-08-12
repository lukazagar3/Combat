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
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow public abstract List<Slot> getSlots();
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void preSlotClick(int slotId, int clickData, net.minecraft.world.inventory.ClickType actionType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = (AbstractContainerMenu)(Object)this;
        boolean isEnderChest = false;
        for (Slot slot : this.getSlots()) {
            if (slot.container instanceof net.minecraft.world.SimpleContainer && 
                slot.container.getClass().getName().contains("EnderChest")) {
                isEnderChest = true;
                break;
            }
        }

        if (isEnderChest) {
            java.util.Set<String> worldLimitedItems = ConfigManager.getConfig().worldLimits.keySet();
            boolean attemptingToPutInEC = false;
            ItemStack stackToMove = ItemStack.EMPTY;

            if (actionType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (!(slot.container.getClass().getName().contains("EnderChest"))) {
                        stackToMove = slot.getItem();
                        attemptingToPutInEC = true;
                    }
                }
            } else if (actionType == net.minecraft.world.inventory.ClickType.PICKUP) {
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (slot.container.getClass().getName().contains("EnderChest")) {
                        stackToMove = this.getCarried();
                        attemptingToPutInEC = true;
                    }
                }
            } else if (actionType == net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                // Dragging in Ender Chest
                if (slotId >= 0 && slotId < this.getSlots().size()) {
                    Slot slot = this.getSlots().get(slotId);
                    if (slot.container.getClass().getName().contains("EnderChest")) {
                        stackToMove = this.getCarried();
                        attemptingToPutInEC = true;
                    }
                }
            }

            if (attemptingToPutInEC && !stackToMove.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stackToMove.getItem()).toString();
                if (worldLimitedItems.contains(itemId)) {
                    serverPlayer.sendSystemMessage(Component.literal("You cannot put world-limited items in an Ender Chest!").withStyle(ChatFormatting.RED));
                    ci.cancel();
                    menu.broadcastChanges();
                }
            }
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"))
    private void postSlotClick(int slotId, int clickData, net.minecraft.world.inventory.ClickType actionType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().itemLimits.entrySet()) {
            enforceInventoryLimit(serverPlayer, entry.getKey(), entry.getValue());
        }
    }

    private void enforceInventoryLimit(ServerPlayer player, String itemId, int limit) {
        int total = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }

        if (total > limit) {
            int excess = total - limit;
            int toRemove = excess;

            // Remove excess from player inventory
            for (int i = player.getInventory().items.size() - 1; i >= 0; i--) {
                if (excess <= 0) break;
                ItemStack stack = player.getInventory().items.get(i);
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                    int count = stack.getCount();
                    if (count <= excess) {
                        excess -= count;
                        player.getInventory().items.set(i, ItemStack.EMPTY);
                    } else {
                        stack.setCount(count - excess);
                        excess = 0;
                    }
                }
            }

            if (excess > 0) {
                for (int i = 0; i < player.getInventory().offhand.size(); i++) {
                    if (excess <= 0) break;
                    ItemStack stack = player.getInventory().offhand.get(i);
                    if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                        int count = stack.getCount();
                        if (count <= excess) {
                            excess -= count;
                            player.getInventory().offhand.set(i, ItemStack.EMPTY);
                        } else {
                            stack.setCount(count - excess);
                            excess = 0;
                        }
                    }
                }
            }

            int finalRemoved = toRemove - excess;
            if (finalRemoved <= 0) return;

            ItemStack excessStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)), finalRemoved);

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
