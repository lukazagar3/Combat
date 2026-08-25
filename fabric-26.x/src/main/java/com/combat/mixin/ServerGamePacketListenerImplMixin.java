package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void onHandSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (this.player == null) return;

        ItemStack stack = this.player.getItemInHand(packet.getHand());
        if (stack == null || stack.isEmpty()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var enchants = stack.get(DataComponents.ENCHANTMENTS);
        boolean hasLunge = enchants != null && enchants.toString().toLowerCase().contains("lunge");

        if ((itemId.contains("spear") || itemId.equals("minecraft:spear") || itemId.equals("minecraft:netherite_spear")) && hasLunge) {
            UUID id = this.player.getUUID();
            long now = System.currentTimeMillis();

            boolean onCooldown = CombatMod.spearLungeExpiration.containsKey(id) && now < CombatMod.spearLungeExpiration.get(id);

            if (onCooldown) {
                // Zero out velocity to freeze forward lunge momentum and sync to client
                this.player.setDeltaMovement(0, this.player.getDeltaMovement().y, 0);
                if (this.player.connection != null) {
                    this.player.connection.send(new ClientboundSetEntityMotionPacket(this.player));
                }

                ci.cancel();
            }
        }
    }
}
