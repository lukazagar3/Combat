package com.example.mcbridge.api;

import java.util.UUID;

public interface IPlayer {
    UUID getUUID();
    String getName();
    void sendSystemMessage(String text, boolean actionBar, ChatColor color, boolean bold);
    boolean hasPermission(int level);
    void kill();
    boolean isItemOnCooldown(String itemType);
    void setItemCooldown(String itemType, double seconds);
    float getFallDistance();
    Object getVanillaPlayer();
    void sendClickableMessage(String title, ClickableOption[] options, String footer);
}
