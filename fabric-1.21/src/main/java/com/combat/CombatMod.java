package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatMod implements ModInitializer {
    public static final Map<UUID, Long> combatTagExpiration = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> immunityExpiration = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> spearLungeExpiration = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> tridentRiptideExpiration = new ConcurrentHashMap<>();
    public static final Map<UUID, UUID> lastAttackerMap = new ConcurrentHashMap<>();
    public static final java.util.Set<UUID> pendingPvPRespawn = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final java.util.Set<UUID> pendingPearlThrows = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Map<UUID, Long> pendingLungeCooldown = new ConcurrentHashMap<>();

    public static MinecraftServer serverInstance = null;

    public static void assignRankIfUnranked(ServerPlayerEntity player) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
        if (data.rankPosition == -1) {
            int nextRank = 1;
            for (PlayerData pd : DataManager.getAllPlayersData().values()) {
                if (pd.rankPosition >= nextRank) {
                    nextRank = pd.rankPosition + 1;
                }
            }
            data.rankPosition = nextRank;
            data.rank = "Rank #" + nextRank;
            data.playerName = player.getName().getString();
            DataManager.save();

            player.sendMessage(Text.literal("You joined the server and received Rank #" + nextRank + "!").formatted(Formatting.GOLD, Formatting.BOLD));
            updatePlayerNametag(player);
        }
    }

    public static void updatePlayerNametag(ServerPlayerEntity player) {
        MinecraftServer server = serverInstance;
        if (server == null) return;
        net.minecraft.scoreboard.Scoreboard scoreboard = server.getScoreboard();
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

    public static int countItemInPlayer(ServerPlayerEntity player, String itemId) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        if (player.currentScreenHandler != null) {
            ItemStack carried = player.currentScreenHandler.getCursorStack();
            if (!carried.isEmpty() && Registries.ITEM.getId(carried.getItem()).toString().equals(itemId)) {
                total += carried.getCount();
            }
        }
        return total;
    }

    public static int getMaxAllowedCount(ServerPlayerEntity player, String itemId) {
        int maxAllowed = Integer.MAX_VALUE;

        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current >= wli.maxCount) {
                return 0;
            }
            if (!DataManager.isOwner(player.getUuid(), itemId)) {
                if (!DataManager.tryAcquire(player.getUuid(), itemId, wli.maxCount)) {
                    return 0;
                } else {
                    maxAllowed = Math.min(maxAllowed, wli.maxCount - current);
                }
            } else {
                maxAllowed = Math.min(maxAllowed, wli.maxCount - current);
            }
        }

        boolean applyInvLimits = true;
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUuid();
            Long combatEnd = combatTagExpiration.get(uuid);
            applyInvLimits = combatEnd != null && System.currentTimeMillis() < combatEnd;
        }

        if (applyInvLimits) {
            Integer invLimit = ConfigManager.getConfig().itemLimits.get(itemId);
            if (invLimit != null && invLimit >= 0) {
                int current = countItemInPlayer(player, itemId);
                maxAllowed = Math.min(maxAllowed, Math.max(0, invLimit - current));
            }
        }

        return maxAllowed;
    }

    public static boolean isItemLimitExceeded(ServerPlayerEntity player, String itemId, int incomingCount) {
        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current + incomingCount > wli.maxCount) {
                return true;
            }
            if (!DataManager.isOwner(player.getUuid(), itemId)) {
                if (!DataManager.tryAcquire(player.getUuid(), itemId, wli.maxCount)) {
                    return true;
                }
            }
        }

        boolean applyInvLimits = true;
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUuid();
            Long combatEnd = combatTagExpiration.get(uuid);
            applyInvLimits = combatEnd != null && System.currentTimeMillis() < combatEnd;
        }

        if (applyInvLimits) {
            Integer invLimit = ConfigManager.getConfig().itemLimits.get(itemId);
            if (invLimit != null && invLimit >= 0) {
                int current = countItemInPlayer(player, itemId);
                if (current + incomingCount > invLimit) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean sanitizeItemEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.component.type.ItemEnchantmentsComponent component = stack.getEnchantments();
        if (component.isEmpty()) return false;

        var configLimits = ConfigManager.getConfig().enchantLimits;
        if (configLimits == null || configLimits.isEmpty()) return false;

        Map<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>, Integer> cappedEnchants = new HashMap<>();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> entry : component.getEnchantmentEntries()) {
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            net.minecraft.util.Identifier id = enchantment.getKey().map(net.minecraft.registry.RegistryKey::getValue).orElse(null);
            if (id != null) {
                String enchantId = id.toString();
                Integer limit = configLimits.get(enchantId);
                if (limit != null) {
                    if (limit == 0) {
                        cappedEnchants.put(enchantment, 0);
                    } else if (level > limit) {
                        cappedEnchants.put(enchantment, limit);
                    }
                }
            }
        }

        if (!cappedEnchants.isEmpty()) {
            net.minecraft.enchantment.EnchantmentHelper.apply(stack, builder -> {
                for (Map.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>, Integer> entry : cappedEnchants.entrySet()) {
                    if (entry.getValue() == 0) {
                        builder.remove(entry.getKey()::equals);
                    } else {
                        builder.set(entry.getKey(), entry.getValue());
                    }
                }
            });
            return true;
        }
        return false;
    }

    public static void checkAndEnforceItemLimits(ServerPlayerEntity player) {
        if (player == null || player.getInventory() == null) return;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty()) {
                sanitizeItemEnchantments(s);
            }
        }

        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUuid();
            Long combatEnd = combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
            if (!inCombat) return;
        }
        if (ConfigManager.getConfig().itemLimits.isEmpty()) return;

        for (Map.Entry<String, Integer> entry : ConfigManager.getConfig().itemLimits.entrySet()) {
            String itemId = entry.getKey();
            int limit = entry.getValue();
            if (limit < 0) continue;

            int total = countItemInPlayer(player, itemId);

            if (total > limit) {
                int excess = total - limit;

                for (int i = player.getInventory().size() - 1; i >= 0; i--) {
                    if (excess <= 0) break;
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                        int count = stack.getCount();
                        int toDrop = Math.min(count, excess);
                        ItemStack dropStack = stack.split(toDrop);
                        player.dropItem(dropStack, false);
                        excess -= toDrop;
                    }
                }

                if (player.currentScreenHandler != null) {
                    player.currentScreenHandler.sendContentUpdates();
                }
            }
        }
    }

    public static void handleKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;

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

                if (serverInstance != null) {
                    serverInstance.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(
                        Text.literal(killer.getName().getString() + " (now Rank #" + victimRank + ") killed " +
                                     victim.getName().getString() + " and took their rank!").formatted(Formatting.GOLD)
                    ));
                }

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
                if (serverInstance != null) {
                    serverInstance.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(
                        Text.literal(killer.getName().getString() + " achieved Rank #" + finalNextRank + "!").formatted(Formatting.GOLD)
                    ));
                }

                updatePlayerNametag(killer);
            }
        }
    }

    @Override
    public void onInitialize() {
        System.out.println("[Combat] Mod initialized!");

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(com.combat.network.LungeBlockPayload.ID, com.combat.network.LungeBlockPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            File configDir = new File(server.getRunDirectory().toFile(), "config/combat");
            ConfigManager.init(configDir);
            DataManager.init(configDir);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            DataManager.onPlayerLogin(player.getUuid());
            assignRankIfUnranked(player);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                UUID uuid = player.getUuid();
                Long combatEnd = combatTagExpiration.get(uuid);
                if (combatEnd != null && System.currentTimeMillis() < combatEnd) {
                    combatTagExpiration.remove(uuid);
                    player.getInventory().dropAll();
                    UUID attackerUuid = lastAttackerMap.get(uuid);
                    if (attackerUuid != null && serverInstance != null) {
                        ServerPlayerEntity attacker = serverInstance.getPlayerManager().getPlayer(attackerUuid);
                        if (attacker != null) {
                            handleKill(attacker, player);
                        }
                    }
                    net.minecraft.server.world.ServerWorld serverWorld = player.getEntityWorld(); if (serverWorld != null) {
                        player.damage(serverWorld, player.getDamageSources().genericKill(), 1000.0F);
                    }
                    server.getPlayerManager().broadcast(
                        Text.literal("[CombatLog] " + player.getName().getString() + " logged out during combat and was killed!").formatted(Formatting.RED, Formatting.BOLD),
                        false
                    );
                }
                DataManager.onPlayerLogout(uuid);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, ubijalec, zrtev, damageSource) -> {
            if (ubijalec instanceof ServerPlayerEntity killerPlayer && zrtev instanceof ServerPlayerEntity victimPlayer) {
                pendingPvPRespawn.add(victimPlayer.getUuid());
                handleKill(killerPlayer, victimPlayer);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                UUID victimUuid = victim.getUuid();
                Long combatEnd = combatTagExpiration.get(victimUuid);
                boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;

                if (inCombat) {
                    UUID attackerUuid = lastAttackerMap.get(victimUuid);
                    if (attackerUuid != null && serverInstance != null) {
                        ServerPlayerEntity killer = serverInstance.getPlayerManager().getPlayer(attackerUuid);
                        if (killer != null && killer != victim) {
                            handleKill(killer, victim);
                        }
                    }
                }
                combatTagExpiration.remove(victimUuid);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((stariIgralec, noviIgralec, alive) -> {
            UUID id = noviIgralec.getUuid();
            combatTagExpiration.remove(id);
            if (pendingPvPRespawn.contains(id)) {
                pendingPvPRespawn.remove(id);
                int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
                if (immunitySeconds > 0) {
                    long immunityEnd = System.currentTimeMillis() + immunitySeconds * 1000L;
                    immunityExpiration.put(id, immunityEnd);
                    noviIgralec.sendMessage(Text.literal("You have " + immunitySeconds + " seconds of protection because a player killed you!").formatted(Formatting.GREEN));
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
                    lastAttackerMap.put(victim.getUuid(), attacker.getUuid());
                    int combatSec = ConfigManager.getConfig().combatLogSeconds;
                    if (combatSec > 0) {
                        long combatEnd = System.currentTimeMillis() + combatSec * 1000L;
                        combatTagExpiration.put(victim.getUuid(), combatEnd);
                        combatTagExpiration.put(attacker.getUuid(), combatEnd);
                    }
                }
                UUID id = victim.getUuid();
                Long exp = immunityExpiration.get(id);
                if (exp != null) {
                    if (System.currentTimeMillis() < exp) {
                        return false;
                    } else {
                        immunityExpiration.remove(id);
                        victim.sendMessage(Text.literal("Your protection has expired!").formatted(Formatting.RED), true);
                    }
                }
            }
            return true;
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                if (entity instanceof ServerPlayerEntity victimPlayer) {
                    lastAttackerMap.put(victimPlayer.getUuid(), serverPlayer.getUuid());
                    int combatSec = ConfigManager.getConfig().combatLogSeconds;
                    if (combatSec > 0) {
                        long combatEnd = System.currentTimeMillis() + combatSec * 1000L;
                        combatTagExpiration.put(serverPlayer.getUuid(), combatEnd);
                        combatTagExpiration.put(victimPlayer.getUuid(), combatEnd);
                    }
                }
                ItemStack mainHand = serverPlayer.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(mainHand.getItem()).toString();

                // Mace Cooldown: ONLY on smash attacks against PLAYERS
                if (mainHand.isOf(Items.MACE) && entity instanceof ServerPlayerEntity) {
                    if (serverPlayer.fallDistance > 1.5F) {
                        Double maceSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:mace");
                        if (maceSec == null || maceSec <= 0.0) maceSec = 5.0;
                        if (serverPlayer.getItemCooldownManager().isCoolingDown(mainHand)) {
                            return ActionResult.FAIL;
                        }
                        serverPlayer.getItemCooldownManager().set(mainHand, (int)(maceSec * 20));
                    }
                }

                }
            return ActionResult.PASS;
        });

        // BLOCK INTERCEPTION (Ender Chest)
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                long now = System.currentTimeMillis();
                net.minecraft.block.BlockState state = world.getBlockState(hitResult.getBlockPos());
                if (state.isOf(net.minecraft.block.Blocks.ENDER_CHEST)) {
                    UUID uuid = serverPlayer.getUuid();
                    Long combatEnd = combatTagExpiration.get(uuid);
                    boolean inCombat = combatEnd != null && now < combatEnd;
                    if (inCombat && !ConfigManager.getConfig().allowEnderchestInCombat) {
                        serverPlayer.sendMessage(Text.literal("Opening Ender Chest is disabled during combat!").formatted(Formatting.RED), true);
                        return ActionResult.FAIL;
                    }
                }
            }
            return ActionResult.PASS;
        });

        // RIGHT CLICK USE INTERCEPTION (Elytra, Fireworks, Ender Pearls)
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                UUID uuid = serverPlayer.getUuid();
                long now = System.currentTimeMillis();
                Long combatEnd = combatTagExpiration.get(uuid);
                boolean inCombat = combatEnd != null && now < combatEnd;

                if (inCombat) {
                    if (!ConfigManager.getConfig().allowElytraInCombat && stack.isOf(Items.ELYTRA)) {
                        serverPlayer.sendMessage(Text.literal("Equipping Elytra is disabled during combat!").formatted(Formatting.RED), true);
                        return ActionResult.FAIL;
                    }
                    if (!ConfigManager.getConfig().allowFireworksInCombat && stack.isOf(Items.FIREWORK_ROCKET)) {
                        serverPlayer.sendMessage(Text.literal("Firework Rockets are disabled during combat!").formatted(Formatting.RED), true);
                        return ActionResult.FAIL;
                    }
                }

                if ("minecraft:ender_pearl".equals(itemId)) {
                    if (inCombat) {
                        Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:ender_pearl");
                        if (cooldownSec != null && cooldownSec > 0.0) {
                            if (!serverPlayer.getItemCooldownManager().isCoolingDown(stack)) {
                                pendingPearlThrows.add(uuid);
                            }
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID uuid : pendingPearlThrows) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
                    if (player.getItemCooldownManager().isCoolingDown(pearl)) {
                        Double pearlSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:ender_pearl");
                        if (pearlSec != null && pearlSec > 0.0) {
                            player.getItemCooldownManager().set(pearl, (int)(pearlSec * 20));
                        }
                    }
                }
            }
            pendingPearlThrows.clear();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("combat")
                .then(CommandManager.literal("rank")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
                            if (data.rankPosition == -1) {
                                context.getSource().sendFeedback(() -> Text.literal("Your Rank: Unranked").formatted(Formatting.YELLOW), false);
                            } else {
                                int pos = data.rankPosition;
                                int extraHearts = Math.max(0, 11 - pos);
                                context.getSource().sendFeedback(() -> Text.literal("Your Rank: Rank #" + pos + " (+" + extraHearts + " Extra Hearts)").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            }
                        }
                        return 1;
                    })
                    .then(CommandManager.literal("top")
                        .executes(context -> {
                            java.util.List<Map.Entry<UUID, PlayerData>> rankedList = new java.util.ArrayList<>();
                            for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
                                if (entry.getValue().rankPosition > 0) {
                                    rankedList.add(entry);
                                }
                            }
                            rankedList.sort(java.util.Comparator.comparingInt(e -> e.getValue().rankPosition));

                            context.getSource().sendFeedback(() -> Text.literal("=== Top 20 Ranked Players ===").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            if (rankedList.isEmpty()) {
                                context.getSource().sendFeedback(() -> Text.literal("No ranked players found.").formatted(Formatting.GRAY), false);
                            } else {
                                int limit = Math.min(20, rankedList.size());
                                for (int i = 0; i < limit; i++) {
                                    Map.Entry<UUID, PlayerData> entry = rankedList.get(i);
                                    UUID uuid = entry.getKey();
                                    PlayerData pd = entry.getValue();
                                    ServerPlayerEntity p = serverInstance != null ? serverInstance.getPlayerManager().getPlayer(uuid) : null;
                                    String name = p != null ? p.getName().getString() : (pd.playerName != null && !pd.playerName.isEmpty() ? pd.playerName : uuid.toString().substring(0, 8));
                                    final int rank = i + 1;
                                    final String displayName = name;
                                    final int rankPos = pd.rankPosition;
                                    context.getSource().sendFeedback(() -> Text.literal(rank + ". " + displayName + " - Rank #" + rankPos).formatted(Formatting.YELLOW), false);
                                }
                            }
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("menu")
                    
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            new com.combat.gui.CombatMenuGui(player).open();
                        }
                        return 1;
                    })
                )
            );
        });
    }
}
