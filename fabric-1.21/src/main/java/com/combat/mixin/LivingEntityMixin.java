package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.of("combat", "ranked_health_boost");

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity player) || player.isDead() || player.getHealth() <= 0.0F || player.isRemoved()) return;

        UUID uuid = player.getUuid();
        if (player.age % 20 == 0) {
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
            player.sendMessage(Text.literal("You are no longer in combat!").formatted(Formatting.GREEN), true);
            inCombatTag = false;
        }

        if (lungeExp != null && now >= lungeExp) {
            com.combat.CombatMod.spearLungeExpiration.remove(uuid);
            lungeCooling = false;
        }

        if (inCombatTag || lungeCooling) {
            net.minecraft.text.MutableText msg = Text.empty();
            if (inCombatTag) {
                long combatSec = (combatEnd - now + 999L) / 1000L;
                msg.append(Text.literal("Combat Tagged: " + combatSec + "s").formatted(Formatting.RED, Formatting.BOLD));
            }
            if (lungeCooling) {
                long lungeSec = (lungeExp - now + 999L) / 1000L;
                if (inCombatTag) {
                    msg.append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY));
                }
                msg.append(Text.literal("Lunge Cooldown: " + lungeSec + "s").formatted(Formatting.GRAY));
            }
            player.sendMessage(msg, true);
        }

        // 2. RANKED HEARTS & EFFECTS
        if (ConfigManager.getConfig().rankedSystemEnabled) {
            PlayerData data = DataManager.getOrCreatePlayerData(uuid);
            int pos = data.rankPosition;

            if (pos > 0 && pos <= 10) {
                int extraHearts = 11 - pos;
                double boostHP = extraHearts * 2.0;

                EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    EntityAttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
                    if (existing == null || existing.value() != boostHP) {
                        if (existing != null) {
                            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                        }
                        maxHealthAttr.addPersistentModifier(new EntityAttributeModifier(
                            HEALTH_MODIFIER_ID,
                            boostHP,
                            EntityAttributeModifier.Operation.ADD_VALUE
                        ));
                        if (player.getHealth() < player.getMaxHealth()) {
                            player.setHealth(player.getMaxHealth());
                        }
                    }
                }

                if (ConfigManager.getConfig().topRanksEffectsEnabled) {
                    List<String> effects = ConfigManager.getConfig().rankPotionEffects.computeIfAbsent(pos, k -> new java.util.ArrayList<>());
                    if (effects.contains("fire_resistance")) {
                        StatusEffectInstance current = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
                        if (current == null || current.getDuration() <= 20) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0, false, false, true));
                        }
                    }
                    if (effects.contains("strength")) {
                        StatusEffectInstance current = player.getStatusEffect(StatusEffects.STRENGTH);
                        if (current == null || current.getDuration() <= 20) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 0, false, false, true));
                        }
                    }
                    if (effects.contains("speed")) {
                        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
                        if (current == null || current.getDuration() <= 20) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 0, false, false, true));
                        }
                    }
                }
            } else {
                EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (maxHealthAttr != null && maxHealthAttr.getModifier(HEALTH_MODIFIER_ID) != null) {
                    maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                }
            }
        }

        // 3. ELYTRA AUTO-UNEQUIP IN COMBAT
        if (!ConfigManager.getConfig().allowElytraInCombat && combatEnd != null && now < combatEnd) {
            ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack.isOf(Items.ELYTRA)) {
                player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.getInventory().insertStack(chestStack);
                if (!chestStack.isEmpty()) {
                    player.dropItem(chestStack, false);
                }
                player.sendMessage(Text.literal("Equipping Elytra is disabled during combat!").formatted(Formatting.RED));
            }
        }
    }
}
