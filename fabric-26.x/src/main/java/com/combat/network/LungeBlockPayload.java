package com.combat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LungeBlockPayload(long timestamp) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LungeBlockPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("combat:block_lunge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LungeBlockPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, LungeBlockPayload::timestamp,
                    LungeBlockPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
