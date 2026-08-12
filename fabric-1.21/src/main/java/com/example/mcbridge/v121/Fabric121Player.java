package com.example.mcbridge.v121;

import com.example.mcbridge.api.ChatColor;
import com.example.mcbridge.api.IPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import java.util.UUID;

public class Fabric121Player implements IPlayer {
    private final ServerPlayerEntity player;

    public Fabric121Player(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public UUID getUUID() {
        return player.getUuid();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public void sendSystemMessage(String text, boolean actionBar, ChatColor color, boolean bold) {
        Formatting formatting = translateColor(color);
        Text component = Text.literal(text).formatted(formatting);
        if (bold) {
            component = Text.literal(text).formatted(formatting, Formatting.BOLD);
        }
        player.sendMessage(component, actionBar);
    }

    @Override
    public boolean hasPermission(int level) {
        net.minecraft.command.permission.PermissionLevel permLevel;
        if (level <= 0) permLevel = net.minecraft.command.permission.PermissionLevel.ALL;
        else if (level == 1) permLevel = net.minecraft.command.permission.PermissionLevel.MODERATORS;
        else if (level == 2) permLevel = net.minecraft.command.permission.PermissionLevel.GAMEMASTERS;
        else if (level == 3) permLevel = net.minecraft.command.permission.PermissionLevel.ADMINS;
        else permLevel = net.minecraft.command.permission.PermissionLevel.OWNERS;
        return player.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(permLevel));
    }

    @Override
    public void kill() {
        player.damage(player.getCommandSource().getWorld(), player.getDamageSources().genericKill(), Float.MAX_VALUE);
    }

    @Override
    public boolean isItemOnCooldown(String itemType) {
        return player.getItemCooldownManager().isCoolingDown(new ItemStack(getItem(itemType)));
    }

    @Override
    public void setItemCooldown(String itemType, double seconds) {
        int ticks = (int) (seconds * 20);
        player.getItemCooldownManager().set(new ItemStack(getItem(itemType)), ticks);
    }

    @Override
    public float getFallDistance() {
        return (float) player.fallDistance;
    }

    @Override
    public Object getVanillaPlayer() {
        return player;
    }

    @Override
    public void sendClickableMessage(String title, com.example.mcbridge.api.ClickableOption[] options, String footer) {
        sendSystemMessage(title, false, ChatColor.GOLD, false);
        net.minecraft.text.MutableText optionsComponent = Text.empty();
        for (com.example.mcbridge.api.ClickableOption opt : options) {
            net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY
                .withClickEvent(new net.minecraft.text.ClickEvent.RunCommand(opt.getCommand()))
                .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal(opt.getTooltip())));
            optionsComponent.append(Text.literal(opt.getLabel()).setStyle(style).formatted(Formatting.AQUA));
        }
        player.sendMessage(optionsComponent, false);
        if (footer != null && !footer.isEmpty()) {
            sendSystemMessage(footer, false, ChatColor.GOLD, false);
        }
    }

    private Item getItem(String itemType) {
        if ("mace".equalsIgnoreCase(itemType)) return Items.MACE;
        if ("ender_pearl".equalsIgnoreCase(itemType)) return Items.ENDER_PEARL;
        throw new IllegalArgumentException("Unknown item type: " + itemType);
    }

    private Formatting translateColor(ChatColor color) {
        switch (color) {
            case RED: return Formatting.RED;
            case GREEN: return Formatting.GREEN;
            case YELLOW: return Formatting.YELLOW;
            case GOLD: return Formatting.GOLD;
            case GRAY: return Formatting.GRAY;
            case AQUA: return Formatting.AQUA;
            default: return Formatting.WHITE;
        }
    }
}
