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
import net.minecraft.resources.ResourceLocation;
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
    public static final Map<UUID, Long> combatTagExpiration = new HashMap<>();
    public static final Map<UUID, Long> immunityExpiration = new HashMap<>();

    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("combat", "rank_health_boost");

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof ServerPlayer targetPlayer)) return;

        if (isImmune(targetPlayer.getUUID())) {
            cir.setReturnValue(false);
            return;
        }

        if (source.getEntity() instanceof ServerPlayer attackerPlayer) {
            if (isImmune(attackerPlayer.getUUID())) {
                cir.setReturnValue(false);
                return;
            }

            long combatEndTime = System.currentTimeMillis() + ConfigManager.getConfig().combatLogSeconds * 1000L;
            combatTagExpiration.put(targetPlayer.getUUID(), combatEndTime);
            combatTagExpiration.put(attackerPlayer.getUUID(), combatEndTime);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer victim)) return;

        combatTagExpiration.remove(victim.getUUID());

        long immunityEndTime = System.currentTimeMillis() + ConfigManager.getConfig().immunitySeconds * 1000L;
        immunityExpiration.put(victim.getUUID(), immunityEndTime);

        if (damageSource.getEntity() instanceof ServerPlayer killer) {
            handleKill(killer, victim);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        Long combatEnd = combatTagExpiration.get(uuid);
        if (combatEnd != null) {
            if (now < combatEnd) {
                int remainingSeconds = (int) Math.ceil((combatEnd - now) / 1000.0);
                player.sendSystemMessage(Component.literal("Combat Tagged: " + remainingSeconds + "s").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            } else {
                combatTagExpiration.remove(uuid);
                player.sendSystemMessage(Component.literal("You are no longer in combat.").withStyle(ChatFormatting.GREEN), true);
            }
        }

        PlayerData data = DataManager.getOrCreatePlayerData(uuid);
        int pos = data.rankPosition;

        AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.removeModifier(HEALTH_MODIFIER_ID);
            if (pos >= 1 && pos <= 10) {
                int hpBoost = ConfigManager.getConfig().rankHealthBoosts.getOrDefault(pos, 0);
                if (hpBoost > 0) {
                    maxHealthAttr.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, hpBoost, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        if (pos >= 1 && pos <= 10) {
            java.util.List<String> effects = ConfigManager.getConfig().rankPotionEffects.get(pos);
            if (effects != null) {
                for (String effectStr : effects) {
                    if ("speed".equalsIgnoreCase(effectStr)) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, false));
                    } else if ("strength".equalsIgnoreCase(effectStr)) {
                        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, false));
                    }
                }
            }
        }

        for (Map.Entry<String, com.combat.CombatConfig.WorldLimitedItem> entry : ConfigManager.getConfig().worldLimits.entrySet()) {
            String itemId = entry.getKey();
            int maxCount = entry.getValue().maxCount;

            boolean hasItem = false;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                    hasItem = true;
                    break;
                }
            }
            if (!hasItem) {
                for (ItemStack stack : player.getInventory().offhand) {
                    if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                        hasItem = true;
                        break;
                    }
                }
            }

            if (hasItem) {
                if (!DataManager.isOwner(uuid, itemId)) {
                    if (DataManager.tryAcquire(uuid, itemId, maxCount)) {
                        player.sendSystemMessage(Component.literal("You are now the registered owner of: " + itemId).withStyle(ChatFormatting.GREEN));
                    } else {
                        player.sendSystemMessage(Component.literal("The global server limit for " + itemId + " has been reached! Item removed.").withStyle(ChatFormatting.RED));
                        for (int i = 0; i < player.getInventory().items.size(); i++) {
                            ItemStack stack = player.getInventory().items.get(i);
                            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                                player.getInventory().items.set(i, ItemStack.EMPTY);
                            }
                        }
                        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
                            ItemStack stack = player.getInventory().offhand.get(i);
                            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                                player.getInventory().offhand.set(i, ItemStack.EMPTY);
                            }
                        }
                        player.containerMenu.broadcastChanges();
                    }
                }
            } else {
                if (DataManager.isOwner(uuid, itemId)) {
                    DataManager.releaseOwnership(uuid, itemId);
                    player.sendSystemMessage(Component.literal("You no longer carry " + itemId + ". Ownership released.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    private boolean isImmune(UUID uuid) {
        Long expiration = immunityExpiration.get(uuid);
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

                killer.getServer().getPlayerList().forEach(p -> p.sendSystemMessage(
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

                killer.getServer().getPlayerList().forEach(p -> p.sendSystemMessage(
                    Component.literal(killer.getName().getString() + " achieved Rank #" + nextRank + "!").withStyle(ChatFormatting.GOLD)
                ));

                updatePlayerNametag(killer);
            }
        }
    }

    private void updatePlayerNametag(ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return;
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUUID());
        String teamName = "c_team_" + player.getUUID().toString().substring(0, 12);

        net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }

        String prefixStr = data.rankPosition == -1 ? "[Unranked] " : "[Rank #" + data.rankPosition + "] ";
        team.setPlayerPrefix(Component.literal(prefixStr).withStyle(ChatFormatting.GOLD));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
