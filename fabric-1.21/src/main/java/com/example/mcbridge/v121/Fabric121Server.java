package com.example.mcbridge.v121;

import com.example.mcbridge.api.ChatColor;
import com.example.mcbridge.api.IPlayer;
import com.example.mcbridge.api.IServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.UUID;

public class Fabric121Server implements IServer {
    private final MinecraftServer server;

    public Fabric121Server(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void broadcastSystemMessage(String text, ChatColor color, boolean bold) {
        Formatting formatting = translateColor(color);
        Text component = Text.literal(text).formatted(formatting);
        if (bold) {
            component = Text.literal(text).formatted(formatting, Formatting.BOLD);
        }
        server.getPlayerManager().broadcast(component, false);
    }

    @Override
    public IPlayer getPlayer(UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        return player == null ? null : new Fabric121Player(player);
    }

    @Override
    public Object getVanillaServer() {
        return server;
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
