package com.combat.util;

import net.minecraft.world.entity.player.Player;

public interface CooldownsPlayerAccessor {
    void combat$setPlayer(Player player);
    Player combat$getPlayer();
}
