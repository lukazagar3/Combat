package com.example.mcbridge.neo121;

import com.example.mcbridge.api.ChatColor;
import com.example.mcbridge.api.IPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.UUID;

public class Neo121Player implements IPlayer {
    private final ServerPlayer player;

    public Neo121Player(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID getUUID() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public void sendSystemMessage(String text, boolean actionBar, ChatColor color, boolean bold) {
        ChatFormatting formatting = translateColor(color);
        Component component = Component.literal(text).withStyle(formatting);
        if (bold) {
            component = Component.literal(text).withStyle(formatting, ChatFormatting.BOLD);
        }
        player.sendSystemMessage(component, actionBar);
    }

    @Override
    public boolean hasPermission(int level) {
        return player.hasPermissions(level);
    }

    @Override
    public void kill() {
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    @Override
    public boolean isItemOnCooldown(String itemType) {
        return player.getCooldowns().isOnCooldown(getItem(itemType));
    }

    @Override
    public void setItemCooldown(String itemType, double seconds) {
        int ticks = (int) (seconds * 20);
        player.getCooldowns().addCooldown(getItem(itemType), ticks);
    }

    @Override
    public float getFallDistance() {
        return player.fallDistance;
    }

    @Override
    public Object getVanillaPlayer() {
        return player;
    }

    @Override
    public void sendClickableMessage(String title, com.example.mcbridge.api.ClickableOption[] options, String footer) {
        sendSystemMessage(title, false, ChatColor.GOLD, false);
        net.minecraft.network.chat.MutableComponent optionsComponent = Component.empty();
        for (com.example.mcbridge.api.ClickableOption opt : options) {
            Style style = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, opt.getCommand()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(opt.getTooltip())));
            optionsComponent.append(Component.literal(opt.getLabel()).withStyle(style));
        }
        player.sendSystemMessage(optionsComponent, false);
        if (footer != null && !footer.isEmpty()) {
            sendSystemMessage(footer, false, ChatColor.GOLD, false);
        }
    }

    private Item getItem(String itemType) {
        if ("mace".equalsIgnoreCase(itemType)) return Items.MACE;
        if ("ender_pearl".equalsIgnoreCase(itemType)) return Items.ENDER_PEARL;
        throw new IllegalArgumentException("Unknown item type: " + itemType);
    }

    private ChatFormatting translateColor(ChatColor color) {
        switch (color) {
            case RED: return ChatFormatting.RED;
            case GREEN: return ChatFormatting.GREEN;
            case YELLOW: return ChatFormatting.YELLOW;
            case GOLD: return ChatFormatting.GOLD;
            case GRAY: return ChatFormatting.GRAY;
            case AQUA: return ChatFormatting.AQUA;
            default: return ChatFormatting.WHITE;
        }
    }
}
