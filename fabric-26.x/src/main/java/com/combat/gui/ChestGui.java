package com.combat.gui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public abstract class ChestGui implements MenuProvider {
    protected final ServerPlayer player;
    protected final String title;
    protected final int rows;
    protected final SimpleContainer inventory;

    public ChestGui(ServerPlayer player, String title, int rows) {
        this.player = player;
        this.title = title;
        this.rows = rows;
        this.inventory = new SimpleContainer(rows * 9);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(title);
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player playerEntity) {
        MenuType<?> type;
        switch (rows) {
            case 1: type = MenuType.GENERIC_9x1; break;
            case 2: type = MenuType.GENERIC_9x2; break;
            case 3: type = MenuType.GENERIC_9x3; break;
            case 4: type = MenuType.GENERIC_9x4; break;
            case 5: type = MenuType.GENERIC_9x5; break;
            default: type = MenuType.GENERIC_9x6; break;
        }

        return new ChestMenu(type, syncId, playerInventory, inventory, rows) {
            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clicked(int slotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
                if (slotId >= 0 && slotId < this.slots.size()) {
                    if (slotId < inventory.getContainerSize()) {
                        handleSlotClick(slotId, clickData, actionType);
                    }
                }
                this.broadcastChanges();
            }
        };
    }

    public void open() {
        setupItems();
        player.openMenu(this);
    }

    protected abstract void setupItems();
    protected abstract void handleSlotClick(int slotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType);
}
