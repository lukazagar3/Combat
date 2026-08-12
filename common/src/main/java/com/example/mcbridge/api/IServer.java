package com.example.mcbridge.api;

import java.util.UUID;

public interface IServer {
    void broadcastSystemMessage(String text, ChatColor color, boolean bold);
    IPlayer getPlayer(UUID uuid);
    Object getVanillaServer();
}
