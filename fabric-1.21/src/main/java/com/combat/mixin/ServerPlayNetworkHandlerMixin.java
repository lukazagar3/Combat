package com.combat.mixin;

import com.combat.CombatMod;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
    private void onHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;

        ItemStack stack = this.player.getStackInHand(packet.getHand());
        if (stack == null || stack.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        boolean hasLunge = stack.hasEnchantments() && stack.getEnchantments().toString().toLowerCase().contains("lunge");

        if ((itemId.contains("spear") || itemId.equals("minecraft:spear") || itemId.equals("minecraft:netherite_spear")) && hasLunge) {
            UUID id = this.player.getUuid();
            long now = System.currentTimeMillis();

            boolean onCooldown = CombatMod.spearLungeExpiration.containsKey(id) && now < CombatMod.spearLungeExpiration.get(id);

            if (onCooldown) {
                // Zero out velocity to freeze forward lunge momentum and sync to client
                this.player.setVelocity(0, this.player.getVelocity().y, 0);
                if (this.player.networkHandler != null) {
                    this.player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(this.player));
                }

                ci.cancel();
            }
        }
    }
}
