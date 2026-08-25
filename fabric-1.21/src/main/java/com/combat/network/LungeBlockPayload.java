package com.combat.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LungeBlockPayload(long timestamp) implements CustomPayload {
    public static final CustomPayload.Id<LungeBlockPayload> ID =
            new CustomPayload.Id<>(Identifier.of("combat", "block_lunge"));

    public static final PacketCodec<RegistryByteBuf, LungeBlockPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_LONG, LungeBlockPayload::timestamp,
                    LungeBlockPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
