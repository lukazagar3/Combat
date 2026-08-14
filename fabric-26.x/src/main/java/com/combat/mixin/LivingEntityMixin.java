package com.combat.mixin;

import com.combat.ConfigManager;
import com.combat.DataManager;
import com.combat.PlayerData;
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

        // Immunity only applies to PvP (damage from another player)
        if (source.getEntity() instanceof ServerPlayer attackerPlayer) {
            if (isImmune(targetPlayer.getUUID())) {
                cir.setReturnValue(false);
                return;
            }
            if (isImmune(attackerPlayer.getUUID())) {
                cir.setReturnValue(false);
                return;
            }

            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            com.combat.CombatMod.combatTagExpiration.put(targetPlayer.getUUID(), combatEndTime);
            com.combat.CombatMod.combatTagExpiration.put(attackerPlayer.getUUID(), combatEndTime);

            net.minecraft.world.item.ItemStack mainHand = attackerPlayer.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (mainHand.is(net.minecraft.world.item.Items.MACE)) {
                attackerPlayer.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MACE), 1);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer victim)) return;

        // Clear combat tag immediately on death
        com.combat.CombatMod.combatTagExpiration.remove(victim.getUUID());

        // Immunity only starts if killed by a player
        if (damageSource.getEntity() instanceof ServerPlayer killer) {
            int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
            if (immunitySeconds > 0) {
                long immunityEndTime = System.currentTimeMillis() + immunitySeconds * 1000L;
                com.combat.CombatMod.immunityExpiration.put(victim.getUUID(), immunityEndTime);
                victim.sendSystemMessage(Component.literal("You were killed by a player! Immunity active for " + immunitySeconds + "s.").withStyle(ChatFormatting.GREEN));
            }
            handleKill(killer, victim);
        }

        // Release world-limit ownership
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

        if (isImmune(uuid)) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 4, false, false, false));
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

        PlayerData data = DataManager.getOrCreatePlayerData(uuid);
        int pos = data.rankPosition;

        AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            AttributeModifier existing = maxHealthAttr.getModifier(HEALTH_MODIFIER_ID);
            int targetHealth = (ConfigManager.getConfig().rankedSystemEnabled && pos >= 1) ? Math.max(2, 40 - 2 * (pos - 1)) : 20;
            int hpBoost = targetHealth - 20;

            if (existing == null || (int) existing.amount() != hpBoost) {
                maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
                if (hpBoost != 0) {
                    maxHealthAttr.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, hpBoost, AttributeModifier.Operation.ADD_VALUE));
                }
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }

        if (ConfigManager.getConfig().rankedSystemEnabled && pos >= 1 && pos <= 10) {
            java.util.List<String> effects = ConfigManager.getConfig().rankPotionEffects.get(pos);
            if (effects != null) {
                for (String effectStr : effects) {
                    if ("speed".equalsIgnoreCase(effectStr)) {
                        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, false));
                    } else if ("strength".equalsIgnoreCase(effectStr)) {
                        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0, false, false, false));
                    }
                }
            }
        }

        for (Map.Entry<String, com.combat.CombatConfig.WorldLimitedItem> entry : ConfigManager.getConfig().worldLimits.entrySet()) {
            String itemId = entry.getKey();
            int maxCount = entry.getValue().maxCount;
            String ownerKey = uuid.toString() + ":" + itemId;

            boolean hasItem = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                    hasItem = true;
                    break;
                }
            }

            if (hasItem) {
                if (!DataManager.isOwner(uuid, itemId)) {
                    if (DataManager.tryAcquire(uuid, itemId, maxCount)) {
                        // Broadcast once to all players on server
                        if (!com.combat.CombatMod.notifiedOwnership.contains(ownerKey)) {
                            com.combat.CombatMod.notifiedOwnership.add(ownerKey);
                            final String displayItem = itemId.replace("minecraft:", "");
                            final String playerName = player.getName().getString();
                            if (com.combat.CombatMod.serverInstance != null) {
                                com.combat.CombatMod.serverInstance.getPlayerList().getPlayers().forEach(p ->
                                    p.sendSystemMessage(Component.literal("[").withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal("World Limit").withStyle(ChatFormatting.YELLOW))
                                        .append(Component.literal("] ").withStyle(ChatFormatting.GOLD))
                                        .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                                        .append(Component.literal(" is now the owner of: ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal(displayItem).withStyle(ChatFormatting.AQUA)))
                                );
                            }
                        }
                    } else {
                        // World limit exceeded - items were already blocked at pickup
                        // Just notify once, don't destroy (prevention happens at pickup/slot-click level)
                        if (!com.combat.CombatMod.notifiedOwnership.contains("denied:" + ownerKey)) {
                            com.combat.CombatMod.notifiedOwnership.add("denied:" + ownerKey);
                            player.sendSystemMessage(Component.literal("World limit reached for ").withStyle(ChatFormatting.RED)
                                .append(Component.literal(itemId.replace("minecraft:", "")).withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("!").withStyle(ChatFormatting.RED)));
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
