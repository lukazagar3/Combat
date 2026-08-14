package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    // In-memory timers for combat tag & immunity


    private static final Identifier HEALTH_MODIFIER_ID = Identifier.of("combat", "rank_health_boost");

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity targetPlayer)) return;

        // Immunity only applies to PvP (damage from another player)
        if (source.getAttacker() instanceof ServerPlayerEntity attackerPlayer) {
            if (isImmune(targetPlayer.getUuid())) {
                cir.setReturnValue(false);
                return;
            }
            if (isImmune(attackerPlayer.getUuid())) {
                cir.setReturnValue(false);
                return;
            }

            // Apply Combat Tag to both players
            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            com.combat.CombatMod.combatTagExpiration.put(targetPlayer.getUuid(), combatEndTime);
            com.combat.CombatMod.combatTagExpiration.put(attackerPlayer.getUuid(), combatEndTime);

            net.minecraft.item.ItemStack mainHand = attackerPlayer.getStackInHand(net.minecraft.util.Hand.MAIN_HAND);
            if (mainHand.isOf(net.minecraft.item.Items.MACE)) {
                attackerPlayer.getItemCooldownManager().set(new net.minecraft.item.ItemStack(net.minecraft.item.Items.MACE), 1);
            }
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity victim)) return;

        // Clear their combat tag on death
        com.combat.CombatMod.combatTagExpiration.remove(victim.getUuid());

        // Immunity only starts if killed by a player
        if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
            int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
            if (immunitySeconds > 0) {
                long immunityEndTime = System.currentTimeMillis() + immunitySeconds * 1000L;
                com.combat.CombatMod.immunityExpiration.put(victim.getUuid(), immunityEndTime);
                victim.sendMessage(Text.literal("You were killed by a player! Immunity active for " + immunitySeconds + "s.").formatted(Formatting.GREEN), false);
            }
            handleKill(killer, victim);
        }

        // Release world-limit ownership tracking
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

        if (isImmune(uuid)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, false, false, false));
        }

        // 1. Render Combat Tag ActionBar
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

        // 2. Apply Ranked Buffs (Extra Max Health + Potion Effects)
        PlayerData data = DataManager.getOrCreatePlayerData(uuid);
        int pos = data.rankPosition;

        // Max Health Boost
        EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            EntityAttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
            int targetHealth = (ConfigManager.getConfig().rankedSystemEnabled && pos >= 1) ? Math.max(2, 40 - 2 * (pos - 1)) : 20;
            int hpBoost = targetHealth - 20;

            if (existing == null || (int) existing.value() != hpBoost) {
                maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                if (hpBoost != 0) {
                    maxHealthAttr.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, hpBoost, EntityAttributeModifier.Operation.ADD_VALUE));
                }
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }

        // Permanent Potion Effects
        if (ConfigManager.getConfig().rankedSystemEnabled && pos >= 1 && pos <= 10) {
            java.util.List<String> effects = ConfigManager.getConfig().rankPotionEffects.get(pos);
            if (effects != null) {
                for (String effectStr : effects) {
                    if ("speed".equalsIgnoreCase(effectStr)) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, false));
                    } else if ("strength".equalsIgnoreCase(effectStr)) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0, false, false, false));
                    }
                }
            }
        }

        // 3. Enforce World Item Limits and Ownership
        for (Map.Entry<String, com.combat.CombatConfig.WorldLimitedItem> entry : ConfigManager.getConfig().worldLimits.entrySet()) {
            String itemId = entry.getKey();
            int maxCount = entry.getValue().maxCount;
            String ownerKey = uuid.toString() + ":" + itemId;

            boolean hasItem = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                    hasItem = true;
                    break;
                }
            }

            if (hasItem) {
                if (!DataManager.isOwner(uuid, itemId)) {
                    if (DataManager.tryAcquire(uuid, itemId, maxCount)) {
                        if (!com.combat.CombatMod.notifiedOwnership.contains(ownerKey)) {
                            com.combat.CombatMod.notifiedOwnership.add(ownerKey);
                            final String displayItem = itemId.replace("minecraft:", "");
                            final String playerName = player.getName().getString();
                            if (com.combat.CombatMod.serverInstance != null) {
                                com.combat.CombatMod.serverInstance.getPlayerManager().getPlayerList().forEach(p ->
                                    p.sendMessage(Text.literal("[")
                                        .append(Text.literal("World Limit").formatted(Formatting.YELLOW))
                                        .append(Text.literal("] ").formatted(Formatting.GOLD))
                                        .append(Text.literal(playerName).formatted(Formatting.WHITE))
                                        .append(Text.literal(" is now the owner of: ").formatted(Formatting.GRAY))
                                        .append(Text.literal(displayItem).formatted(Formatting.AQUA)), false)
                                );
                            }
                        }
                    } else {
                        if (!com.combat.CombatMod.notifiedOwnership.contains("denied:" + ownerKey)) {
                            com.combat.CombatMod.notifiedOwnership.add("denied:" + ownerKey);
                            player.sendMessage(Text.literal("World limit reached for ")
                                .append(Text.literal(itemId.replace("minecraft:", "")).formatted(Formatting.YELLOW))
                                .append(Text.literal("!").formatted(Formatting.RED)), false);
                        }
                    }
                }
            } else {
                if (DataManager.isOwner(uuid, itemId)) {
                    DataManager.releaseOwnership(uuid, itemId);
                    com.combat.CombatMod.notifiedOwnership.remove(ownerKey);
                    com.combat.CombatMod.notifiedOwnership.remove("denied:" + ownerKey);
                }
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

                com.combat.CombatMod.serverInstance.getPlayerManager().broadcast(
                    Text.literal(killer.getName().getString() + " (now Rank #" + victimRank + ") killed " +
                                 victim.getName().getString() + " and took their rank!").formatted(Formatting.GOLD),
                    false
                );

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

                com.combat.CombatMod.serverInstance.getPlayerManager().broadcast(
                    Text.literal(killer.getName().getString() + " achieved Rank #" + nextRank + "!").formatted(Formatting.GOLD),
                    false
                );

                updatePlayerNametag(killer);
            }
        }
    }

    private void updatePlayerNametag(ServerPlayerEntity player) {
        net.minecraft.server.MinecraftServer server = com.combat.CombatMod.serverInstance;
        if (server == null) return;
        net.minecraft.scoreboard.Scoreboard scoreboard = server.getScoreboard();
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
        String teamName = "c_team_" + player.getUuid().toString().substring(0, 12);

        net.minecraft.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
        }

        if (!ConfigManager.getConfig().rankedSystemEnabled) {
            team.setPrefix(Text.literal("").formatted(Formatting.GOLD));
        } else {
            String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
            team.setPrefix(Text.literal(prefixStr).formatted(Formatting.GOLD));
        }
        scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
    }
}
