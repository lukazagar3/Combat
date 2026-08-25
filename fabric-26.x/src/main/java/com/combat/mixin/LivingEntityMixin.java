package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.parse("combat:ranked_health_boost");

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer player) || player.isDeadOrDying() || player.getHealth() <= 0.0F || player.isRemoved()) return;

        UUID uuid = player.getUUID();
        if (player.tickCount % 20 == 0) {
            com.combat.CombatMod.checkAndEnforceItemLimits(player);
        }

        long now = System.currentTimeMillis();

        // 1. COMBAT TAG & SPEAR LUNGE ACTIONBAR TIMER
        Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
        Long lungeExp = com.combat.CombatMod.spearLungeExpiration.get(uuid);

        boolean inCombatTag = combatEnd != null && now < combatEnd;
        boolean lungeCooling = lungeExp != null && now < lungeExp;

        if (combatEnd != null && now >= combatEnd) {
            com.combat.CombatMod.combatTagExpiration.remove(uuid);
            player.sendSystemMessage(Component.literal("You are no longer in combat!").withStyle(ChatFormatting.GREEN), true);
            inCombatTag = false;
        }

        if (lungeExp != null && now >= lungeExp) {
            com.combat.CombatMod.spearLungeExpiration.remove(uuid);
            lungeCooling = false;
        }

        if (inCombatTag || lungeCooling) {
            net.minecraft.network.chat.MutableComponent msg = Component.empty();
            if (inCombatTag) {
                long combatSec = (combatEnd - now + 999L) / 1000L;
                msg.append(Component.literal("Combat Tagged: " + combatSec + "s").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            if (lungeCooling) {
                long lungeSec = (lungeExp - now + 999L) / 1000L;
                if (inCombatTag) {
                    msg.append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY));
                }
                msg.append(Component.literal("Lunge Cooldown: " + lungeSec + "s").withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(msg, true);
        }

        // 2. RANKED HEARTS & EFFECTS
        if (ConfigManager.getConfig().rankedSystemEnabled) {
            PlayerData data = DataManager.getOrCreatePlayerData(uuid);
            int pos = data.rankPosition;

            if (pos > 0 && pos <= 10) {
                int extraHearts = 11 - pos;
                double boostHP = extraHearts * 2.0;

                AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    AttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
                    if (existing == null || existing.amount() != boostHP) {
                        if (existing != null) {
                            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                        }
                        maxHealthAttr.addPermanentModifier(new AttributeModifier(
                            HEALTH_MODIFIER_ID,
                            boostHP,
                            AttributeModifier.Operation.ADD_VALUE
                        ));
                        if (player.getHealth() < player.getMaxHealth()) {
                            player.setHealth(player.getMaxHealth());
                        }
                    }
                }

                if (ConfigManager.getConfig().topRanksEffectsEnabled) {
                    List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new java.util.ArrayList<>());
                    if (effects.contains("fire_resistance")) {
                        MobEffectInstance current = player.getEffect(MobEffects.FIRE_RESISTANCE);
                        if (current == null || current.getDuration() <= 20) {
                            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 300, 0, false, false, true));
                        }
                    }
                    if (effects.contains("strength")) {
                        MobEffectInstance current = player.getEffect(MobEffects.STRENGTH);
                        if (current == null || current.getDuration() <= 20) {
                            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 300, 0, false, false, true));
                        }
                    }
                    if (effects.contains("speed")) {
                        MobEffectInstance current = player.getEffect(MobEffects.SPEED);
                        if (current == null || current.getDuration() <= 20) {
                            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 0, false, false, true));
                        }
                    }
                }
            } else {
                AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null && maxHealthAttr.getModifier(HEALTH_MODIFIER_ID) != null) {
                    maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                }
            }
        }

        // 3. ELYTRA AUTO-UNEQUIP IN COMBAT
        if (!ConfigManager.getConfig().allowElytraInCombat && combatEnd != null && now < combatEnd) {
            ItemStack chestStack = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (chestStack.is(net.minecraft.world.item.Items.ELYTRA)) {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.getInventory().add(chestStack);
                if (!chestStack.isEmpty()) {
                    player.drop(chestStack, false);
                }
                player.sendSystemMessage(Component.literal("Equipping Elytra is disabled during combat!").withStyle(ChatFormatting.RED));
            }
        }
    }


}
