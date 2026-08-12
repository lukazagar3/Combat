package com.combat.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class ChestGui implements NamedScreenHandlerFactory {
    protected final ServerPlayerEntity player;
    protected final String title;
    protected final int rows;
    protected final SimpleInventory inventory;

    public ChestGui(ServerPlayerEntity player, String title, int rows) {
        this.player = player;
        this.title = title;
        this.rows = rows;
        this.inventory = new SimpleInventory(rows * 9);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal(title);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        ScreenHandlerType<?> type;
        switch (rows) {
            case 1: type = ScreenHandlerType.GENERIC_9X1; break;
            case 2: type = ScreenHandlerType.GENERIC_9X2; break;
            case 3: type = ScreenHandlerType.GENERIC_9X3; break;
            case 4: type = ScreenHandlerType.GENERIC_9X4; break;
            case 5: type = ScreenHandlerType.GENERIC_9X5; break;
            default: type = ScreenHandlerType.GENERIC_9X6; break;
        }

        return new GenericContainerScreenHandler(type, syncId, playerInventory, inventory, rows) {
            @Override
            public boolean canUse(PlayerEntity player) {
                return true;
            }

            @Override
            public void onSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
                if (slotId >= 0 && slotId < this.slots.size()) {
                    if (slotId < inventory.size()) {
                        handleSlotClick(slotId, clickData, actionType);
                    }
                }
                // Correct client side prediction
                this.sendContentUpdates();
            }
        };
    }

    public void open() {
        setupItems();
        player.openHandledScreen(this);
    }

    protected abstract void setupItems();
    protected abstract void handleSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType);
}
