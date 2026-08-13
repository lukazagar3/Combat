package com.combat.gui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;

public abstract class AnvilInputGui implements MenuProvider {
    protected final ServerPlayer player;
    protected final String title;
    protected final String defaultText;

    public AnvilInputGui(ServerPlayer player, String title, String defaultText) {
        this.player = player;
        this.title = title;
        this.defaultText = defaultText;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(title);
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player playerEntity) {
        AnvilMenu menu = new AnvilMenu(syncId, playerInventory) {
            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clicked(int slotId, int clickData, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
                if (slotId == 2) {
                    ItemStack output = getSlot(2).getItem();
                    ItemStack inputSlot = getSlot(0).getItem();
                    String inputStr = !output.isEmpty() ? output.getHoverName().getString() : (!inputSlot.isEmpty() ? inputSlot.getHoverName().getString() : "");
                    handleInput(inputStr);
                } else {
                    super.clicked(slotId, clickData, actionType, player);
                }
            }
        };

        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(defaultText));
        menu.getSlot(0).set(paper);
        return menu;
    }

    public void open() {
        player.openMenu(this);
    }

    protected abstract void handleInput(String input);
}
