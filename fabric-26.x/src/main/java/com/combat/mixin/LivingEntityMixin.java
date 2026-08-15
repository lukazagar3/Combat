package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    private static final net.minecraft.resources.Identifier HEALTH_MODIFIER_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("combat", "rank_health_boost");

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof ServerPlayer targetPlayer)) return;

        Entity rawAttacker = source.getEntity();
        if (rawAttacker instanceof ServerPlayer attackerPlayer) {
            if (isImmune(targetPlayer.getUUID())) {
                targetPlayer.sendSystemMessage(Component.literal("You are immune to PvP damage!").withStyle(ChatFormatting.GREEN));
                cir.setReturnValue(false);
                return;
            }
            if (isImmune(attackerPlayer.getUUID())) {
                attackerPlayer.sendSystemMessage(Component.literal("You cannot attack while immune!").withStyle(ChatFormatting.RED));
                cir.setReturnValue(false);
                return;
            }

            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            com.combat.CombatMod.combatTagExpiration.put(targetPlayer.getUUID(), combatEndTime);
            com.combat.CombatMod.combatTagExpiration.put(attackerPlayer.getUUID(), combatEndTime);

            // Trigger Mace Smash Cooldown AFTER damage is dealt!
            ItemStack mainHand = attackerPlayer.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (mainHand.is(net.minecraft.world.item.Items.MACE) && attackerPlayer.fallDistance > 1.5F) {
                Double maceSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:mace");
                if (maceSec != null && maceSec > 0.0) {
                    attackerPlayer.getCooldowns().addCooldown(mainHand, (int)(maceSec * 20));
                }
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer victim)) return;

        com.combat.CombatMod.combatTagExpiration.remove(victim.getUUID());

        Entity rawAttacker = damageSource.getEntity();
        if (rawAttacker instanceof ServerPlayer killer) {
            int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
            if (immunitySeconds > 0) {
                long immunityEndTime = System.currentTimeMillis() + immunitySeconds * 1000L;
                com.combat.CombatMod.immunityExpiration.put(victim.getUUID(), immunityEndTime);
                victim.sendSystemMessage(Component.literal("You were killed by a player! Immunity active for " + immunitySeconds + "s.").withStyle(ChatFormatting.GREEN));
            }
            handleKill(killer, victim);
        }

        com.combat.CombatMod.notifiedOwnership.removeIf(key -> key.startsWith(victim.getUUID().toString()));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer player)) return;

        com.combat.CombatMod.checkAndEnforceItemLimits(player);

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            com.combat.CombatMod.combatTagExpiration.remove(uuid);
        }

        Long immunityEnd = com.combat.CombatMod.immunityExpiration.get(uuid);
        if (immunityEnd != null) {
            if (now < immunityEnd) {
                int remainingSeconds = (int) Math.ceil((immunityEnd - now) / 1000.0);
                player.sendSystemMessage(Component.literal("PvP Immunity Active: " + remainingSeconds + "s").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 4, false, false, false));
            } else {
                com.combat.CombatMod.immunityExpiration.remove(uuid);
                player.sendSystemMessage(Component.literal("PvP Immunity expired.").withStyle(ChatFormatting.YELLOW));
            }
        }

        Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
        if (combatEnd != null) {
            if (now < combatEnd) {
                int remainingSeconds = (int) Math.ceil((combatEnd - now) / 1000.0);
                player.sendSystemMessage(Component.literal("Combat Tagged: " + remainingSeconds + "s").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            } else {
                com.combat.CombatMod.combatTagExpiration.remove(uuid);
                player.sendSystemMessage(Component.literal("You are no longer in combat.").withStyle(ChatFormatting.GREEN), true);
            }
        }

        if (ConfigManager.getConfig().rankedSystemEnabled) {
            com.combat.CombatMod.assignRankIfUnranked(player);

            PlayerData data = DataManager.getOrCreatePlayerData(uuid);
            int pos = data.rankPosition;

            if (pos != -1) {
                double boostHP = Math.max(0, (11 - pos) * 2.0);
                AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    AttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
                    if (existing == null || existing.amount() != boostHP) {
                        if (existing != null) {
                            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                        }
                        if (boostHP > 0) {
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
                }

                if (pos == 1) {
                    player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false, true));
                } else if (pos == 2) {
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, false, false, true));
                } else if (pos == 3) {
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, true));
                }
            }
        } else {
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getModifier(HEALTH_MODIFIER_ID) != null) {
                maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
            }
        }
    }

    private boolean isImmune(UUID uuid) {
        Long expiration = com.combat.CombatMod.immunityExpiration.get(uuid);
        return expiration != null && System.currentTimeMillis() < expiration;
    }

    private void handleKill(ServerPlayer killer, ServerPlayer victim) {
        PlayerData killerData = DataManager.getOrCreatePlayerData(killer.getUUID());
        PlayerData victimData = DataManager.getOrCreatePlayerData(victim.getUUID());

        int killerRank = killerData.rankPosition;
        int victimRank = victimData.rankPosition;

        if (victimRank != -1) {
            if (killerRank == -1 || killerRank > victimRank) {
                killerData.rankPosition = victimRank;
                killerData.rank = "Rank #" + victimRank;

                victimData.rankPosition = killerRank;
                victimData.rank = killerRank == -1 ? "unranked" : "Rank #" + killerRank;

                DataManager.save();

                com.combat.CombatMod.serverInstance.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(
                    Component.literal(killer.getName().getString() + " (now Rank #" + victimRank + ") killed " +
                                 victim.getName().getString() + " and took their rank!").withStyle(ChatFormatting.GOLD)
                ));

                updatePlayerNametag(killer);
                updatePlayerNametag(victim);
            }
        } else {
            if (killerRank == -1) {
                int nextRank = 1;
                for (PlayerData pd : DataManager.getAllPlayersData().values()) {
                    if (pd.rankPosition >= nextRank) {
                        nextRank = pd.rankPosition + 1;
                    }
                }
                killerData.rankPosition = nextRank;
                killerData.rank = "Rank #" + nextRank;
                DataManager.save();

                final int finalNextRank = nextRank;
                com.combat.CombatMod.serverInstance.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(
                    Component.literal(killer.getName().getString() + " achieved Rank #" + finalNextRank + "!").withStyle(ChatFormatting.GOLD)
                ));

                updatePlayerNametag(killer);
            }
        }
    }

    private void updatePlayerNametag(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = com.combat.CombatMod.serverInstance;
        if (server == null) return;
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUUID());
        String teamName = "c_team_" + player.getUUID().toString().substring(0, 12);

        net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        if (!ConfigManager.getConfig().rankedSystemEnabled) {
            team.setPlayerPrefix(Component.literal(""));
        } else {
            String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
            team.setPlayerPrefix(Component.literal(prefixStr).withStyle(ChatFormatting.GOLD));
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
