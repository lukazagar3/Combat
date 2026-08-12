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
    public static final Map<UUID, Long> combatTagExpiration = new HashMap<>();
    public static final Map<UUID, Long> immunityExpiration = new HashMap<>();

    private static final Identifier HEALTH_MODIFIER_ID = Identifier.of("combat", "rank_health_boost");

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity targetPlayer)) return;

        // 1. Check Immunity for Target Player
        if (isImmune(targetPlayer.getUuid())) {
            cir.setReturnValue(false);
            return;
        }

        // 2. Check Immunity for Attacker (if player)
        if (source.getAttacker() instanceof ServerPlayerEntity attackerPlayer) {
            if (isImmune(attackerPlayer.getUuid())) {
                cir.setReturnValue(false);
                return;
            }

            // Apply Combat Tag to both players
            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            combatTagExpiration.put(targetPlayer.getUuid(), combatEndTime);
            combatTagExpiration.put(attackerPlayer.getUuid(), combatEndTime);
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity victim)) return;

        // Clear their combat tag on death
        combatTagExpiration.remove(victim.getUuid());

        // Apply post-respawn immunity
        long immunityEndTime = System.currentTimeMillis() + ConfigManager.getConfig().immunitySeconds * 1000L;
        immunityExpiration.put(victim.getUuid(), immunityEndTime);

        // Rank Swapping Logic on Kill
        if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
            handleKill(killer, victim);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayerEntity player)) return;

        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();

        // 1. Render Combat Tag ActionBar
        Long combatEnd = combatTagExpiration.get(uuid);
        if (combatEnd != null) {
            if (now < combatEnd) {
                int remainingSeconds = (int) Math.ceil((combatEnd - now) / 1000.0);
                player.sendMessage(Text.literal("Combat Tagged: " + remainingSeconds + "s").formatted(Formatting.RED, Formatting.BOLD), true);
            } else {
                combatTagExpiration.remove(uuid);
                player.sendMessage(Text.literal("You are no longer in combat.").formatted(Formatting.GREEN), true);
            }
        }

        // 2. Apply Ranked Buffs (Extra Max Health + Potion Effects)
        PlayerData data = DataManager.getOrCreatePlayerData(uuid);
        int pos = data.rankPosition;

        // Max Health Boost
        EntityAttributeInstance maxHealthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
            if (pos >= 1 && pos <= 10) {
                int hpBoost = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
                if (hpBoost > 0) {
                    maxHealthAttr.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, hpBoost, EntityAttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // Permanent Potion Effects
        if (pos >= 1 && pos <= 10) {
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

            boolean hasItem = false;
            for (int i = 0; i < player.getInventory().main.size(); i++) {
                ItemStack stack = player.getInventory().main.get(i);
                if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                    hasItem = true;
                    break;
                }
            }
            if (!hasItem) {
                for (ItemStack stack : player.getInventory().offHand) {
                    if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                        hasItem = true;
                        break;
                    }
                }
            }

            if (hasItem) {
                if (!DataManager.isOwner(uuid, itemId)) {
                    if (DataManager.tryAcquire(uuid, itemId, maxCount)) {
                        player.sendMessage(Text.literal("You are now the registered owner of: " + itemId).formatted(Formatting.GREEN), false);
                    } else {
                        player.sendMessage(Text.literal("The global server limit for " + itemId + " has been reached! Item removed.").formatted(Formatting.RED), false);
                        for (int i = 0; i < player.getInventory().main.size(); i++) {
                            ItemStack stack = player.getInventory().main.get(i);
                            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                                player.getInventory().main.set(i, ItemStack.EMPTY);
                            }
                        }
                        for (int i = 0; i < player.getInventory().offHand.size(); i++) {
                            ItemStack stack = player.getInventory().offHand.get(i);
                            if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                                player.getInventory().offHand.set(i, ItemStack.EMPTY);
                            }
                        }
                        player.currentScreenHandler.sendContentUpdates();
                    }
                }
            } else {
                if (DataManager.isOwner(uuid, itemId)) {
                    DataManager.releaseOwnership(uuid, itemId);
                    player.sendMessage(Text.literal("You no longer carry " + itemId + ". Ownership released.").formatted(Formatting.YELLOW), false);
                }
            }
        }
    }

    private boolean isImmune(UUID uuid) {
        Long expiration = immunityExpiration.get(uuid);
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

                killer.getServer().getPlayerManager().broadcast(
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

                killer.getServer().getPlayerManager().broadcast(
                    Text.literal(killer.getName().getString() + " achieved Rank #" + nextRank + "!").formatted(Formatting.GOLD),
                    false
                );

                updatePlayerNametag(killer);
            }
        }
    }

    private void updatePlayerNametag(ServerPlayerEntity player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return;
        net.minecraft.scoreboard.Scoreboard scoreboard = server.getScoreboard();
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
        String teamName = "c_team_" + player.getUuid().toString().substring(0, 12);

        net.minecraft.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
        }

        String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
        team.setPrefix(Text.literal(prefixStr).formatted(Formatting.GOLD));
        scoreboard.addPlayerToTeam(player.getNameForScoreboard(), team);
    }
}
