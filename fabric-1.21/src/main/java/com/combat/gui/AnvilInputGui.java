package com.combat.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public abstract class AnvilInputGui implements NamedScreenHandlerFactory {
    protected final ServerPlayerEntity player;
    protected final String title;
    protected final String defaultText;

    public AnvilInputGui(ServerPlayerEntity player, String title, String defaultText) {
        this.player = player;
        this.title = title;
        this.defaultText = defaultText;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal(title);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        AnvilScreenHandler handler = new AnvilScreenHandler(syncId, playerInventory) {
            @Override
            public boolean canUse(PlayerEntity player) {
                return true;
            }

            @Override
            public void onSlotClick(int slotId, int clickData, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
                if (slotId == 2) {
                    ItemStack output = getSlot(2).getStack();
                    if (!output.isEmpty()) {
                        String input = output.getName().getString();
                        handleInput(input);
                        player.closeHandledScreen();
                    }
                } else {
                    super.onSlotClick(slotId, clickData, actionType, player);
                }
            }
        };

        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(defaultText));
        handler.getSlot(0).setStack(paper);
        return handler;
    }

    public void open() {
        player.openHandledScreen(this);
    }

    protected abstract void handleInput(String input);
}
