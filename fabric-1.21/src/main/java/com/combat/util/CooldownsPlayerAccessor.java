package com.combat.util;

import net.minecraft.entity.player.PlayerEntity;

public interface CooldownsPlayerAccessor {
    void combat$setPlayer(PlayerEntity player);
    PlayerEntity combat$getPlayer();
}
