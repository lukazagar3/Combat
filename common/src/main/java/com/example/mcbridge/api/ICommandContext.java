package com.example.mcbridge.api;

public interface ICommandContext {
    IPlayer getPlayerSender();
    void sendSuccess(String text, ChatColor color);
}
