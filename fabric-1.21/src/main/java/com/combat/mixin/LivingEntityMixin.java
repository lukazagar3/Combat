package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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

    private static final Identifier HEALTH_MODIFIER_ID = Identifier.of("combat", "rank_health_boost");

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity targetPlayer)) return;

        Entity rawAttacker = source.getAttacker() != null ? source.getAttacker() : source.getSource();
        if (rawAttacker instanceof ServerPlayerEntity attackerPlayer) {
            if (isImmune(targetPlayer.getUuid())) {
                targetPlayer.sendMessage(Text.literal("You are immune to PvP damage!").formatted(Formatting.GREEN), false);
                cir.setReturnValue(false);
                return;
            }
            if (isImmune(attackerPlayer.getUuid())) {
                attackerPlayer.sendMessage(Text.literal("You cannot attack while immune!").formatted(Formatting.RED), false);
                cir.setReturnValue(false);
                return;
            }

            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            com.combat.CombatMod.combatTagExpiration.put(targetPlayer.getUuid(), combatEndTime);
            com.combat.CombatMod.combatTagExpiration.put(attackerPlayer.getUuid(), combatEndTime);

            // Trigger Mace Smash Cooldown AFTER damage is dealt!
            ItemStack mainHand = attackerPlayer.getStackInHand(net.minecraft.util.Hand.MAIN_HAND);
            if (mainHand.isOf(net.minecraft.item.Items.MACE) && attackerPlayer.fallDistance > 1.5F) {
                Double maceSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:mace");
                if (maceSec != null && maceSec > 0.0) {
                    attackerPlayer.getItemCooldownManager().set(mainHand, (int)(maceSec * 20));
                }
            }
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity victim)) return;

        com.combat.CombatMod.combatTagExpiration.remove(victim.getUuid());

        Entity rawAttacker = damageSource.getAttacker() != null ? damageSource.getAttacker() : damageSource.getSource();
        if (rawAttacker instanceof ServerPlayerEntity killer) {
            int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
            if (immunitySeconds > 0) {
                long immunityEndTime = System.currentTimeMillis() + immunitySeconds * 1000L;
                com.combat.CombatMod.immunityExpiration.put(victim.getUuid(), immunityEndTime);
                victim.sendMessage(Text.literal("You were killed by a player! Immunity active for " + immunitySeconds + "s.").formatted(Formatting.GREEN), false);
            }
            handleKill(killer, victim);
        }

        com.combat.CombatMod.notifiedOwnership.removeIf(key -> key.startsWith(victim.getUuid().toString()));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity player)) return;

        com.combat.CombatMod.checkAndEnforceItemLimits(player);

        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();

        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            com.combat.CombatMod.combatTagExpiration.remove(uuid);
        }

        Long immunityEnd = com.combat.CombatMod.immunityExpiration.get(uuid);
        if (immunityEnd != null) {
            if (now < immunityEnd) {
                int remainingSeconds = (int) Math.ceil((immunityEnd - now) / 1000.0);
                player.sendMessage(Text.literal("PvP Immunity Active: " + remainingSeconds + "s").formatted(Formatting.GREEN, Formatting.BOLD), true);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, false, false, false));
            } else {
                com.combat.CombatMod.immunityExpiration.remove(uuid);
                player.sendMessage(Text.literal("PvP Immunity expired.").formatted(Formatting.YELLOW), false);
            }
        }

        Long combatEnd = com.combat.CombatMod.combatTagExpiration.get(uuid);
        if (combatEnd != null) {
            if (now < combatEnd) {
                int remainingSeconds = (int) Math.ceil((combatEnd - now) / 1000.0);
                player.sendMessage(Text.literal("Combat Tagged: " + remainingSeconds + "s").formatted(Formatting.RED, Formatting.BOLD), true);
            } else {
                com.combat.CombatMod.combatTagExpiration.remove(uuid);
                player.sendMessage(Text.literal("You are no longer in combat.").formatted(Formatting.GREEN), true);
            }
        }

        if (ConfigManager.getConfig().rankedSystemEnabled) {
            com.combat.CombatMod.assignRankIfUnranked(player);

            PlayerData data = DataManager.getOrCreatePlayerData(uuid);
            int pos = data.rankPosition;

            if (pos != -1) {
                double boostHP = Math.max(0, (11 - pos) * 2.0);
                EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    EntityAttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
                    if (existing == null || existing.value() != boostHP) {
                        if (existing != null) {
                            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                        }
                        if (boostHP > 0) {
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
                }

                if (pos == 1) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0, false, false, true));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, true));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, false, false, true));
                } else if (pos == 2) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, true));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, false, false, true));
                } else if (pos == 3) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, true));
                }
            }
        } else {
            EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getModifier(HEALTH_MODIFIER_ID) != null) {
                maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
            }
        }
    }

    private boolean isImmune(UUID uuid) {
        Long expiration = com.combat.CombatMod.immunityExpiration.get(uuid);
        return expiration != null && System.currentTimeMillis() < expiration;
    }

    private void handleKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        PlayerData killerData = DataManager.getOrCreatePlayerData(killer.getUuid());
        PlayerData victimData = DataManager.getOrCreatePlayerData(victim.getUuid());

        int killerRank = killerData.rankPosition;
        int victimRank = victimData.rankPosition;

        if (victimRank != -1) {
            if (killerRank == -1 || killerRank > victimRank) {
                killerData.rankPosition = victimRank;
                killerData.rank = "Rank #" + victimRank;

                victimData.rankPosition = killerRank;
                victimData.rank = killerRank == -1 ? "unranked" : "Rank #" + killerRank;

                DataManager.save();

                com.combat.CombatMod.serverInstance.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(
                    Text.literal(killer.getName().getString() + " (now Rank #" + victimRank + ") killed " +
                                 victim.getName().getString() + " and took their rank!").formatted(Formatting.GOLD), false
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
                com.combat.CombatMod.serverInstance.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(
                    Text.literal(killer.getName().getString() + " achieved Rank #" + finalNextRank + "!").formatted(Formatting.GOLD), false
                ));

                updatePlayerNametag(killer);
            }
        }
    }

    private void updatePlayerNametag(ServerPlayerEntity player) {
        MinecraftServer server = com.combat.CombatMod.serverInstance;
        if (server == null) return;
        net.minecraft.scoreboard.ServerScoreboard scoreboard = server.getScoreboard();
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
        String teamName = "c_team_" + player.getUuid().toString().substring(0, 12);

        net.minecraft.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
        }

        if (!ConfigManager.getConfig().rankedSystemEnabled) {
            team.setPrefix(Text.literal(""));
        } else {
            String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
            team.setPrefix(Text.literal(prefixStr).formatted(Formatting.GOLD));
        }
        scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
    }
}
