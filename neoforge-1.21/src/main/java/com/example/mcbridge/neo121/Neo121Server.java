package com.example.mcbridge.neo121;

import com.example.mcbridge.api.ChatColor;
import com.example.mcbridge.api.IPlayer;
import com.example.mcbridge.api.IServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.UUID;

public class Neo121Server implements IServer {
    private final MinecraftServer server;

    public Neo121Server(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void broadcastSystemMessage(String text, ChatColor color, boolean bold) {
        ChatFormatting formatting = translateColor(color);
        Component component = Component.literal(text).withStyle(formatting);
        if (bold) {
            component = Component.literal(text).withStyle(formatting, ChatFormatting.BOLD);
        }
        server.getPlayerList().broadcastSystemMessage(component, false);
    }

    @Override
    public IPlayer getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player == null ? null : new Neo121Player(player);
    }

    @Override
    public Object getVanillaServer() {
        return server;
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
