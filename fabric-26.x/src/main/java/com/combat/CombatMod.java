package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.util.Map;
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
    // Pending cooldowns applied at end-of-tick so the current attack goes through first
    public static final Map<UUID, Integer> pendingMaceCooldown = new ConcurrentHashMap<>();

    public static MinecraftServer serverInstance = null;

    public static void assignRankIfUnranked(ServerPlayer player) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;
        PlayerData data = DataManager.getOrCreatePlayerData(player.getUUID());
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

            player.sendSystemMessage(Component.literal("You joined the server and received Rank #" + nextRank + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            updatePlayerNametag(player);
        }
    }

    public static void updatePlayerNametag(ServerPlayer player) {
        MinecraftServer server = serverInstance;
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

    public static int countItemInPlayer(ServerPlayer player, String itemId) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && BuiltInRegistries.ITEM.getKey(carried.getItem()).toString().equals(itemId)) {
                total += carried.getCount();
            }
        }
        return total;
    }

    public static int getMaxAllowedCount(ServerPlayer player, String itemId) {
        int maxAllowed = Integer.MAX_VALUE;

        // World limits are ALWAYS enforced 24/7 regardless of limitsOnlyInCombat
        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current >= wli.maxCount) {
                return 0;
            }
            if (!DataManager.isOwner(player.getUUID(), itemId)) {
                if (!DataManager.tryAcquire(player.getUUID(), itemId, wli.maxCount)) {
                    return 0;
                } else {
                    maxAllowed = Math.min(maxAllowed, wli.maxCount - current);
                }
            } else {
                maxAllowed = Math.min(maxAllowed, wli.maxCount - current);
            }
        }

        // Inventory item limits are subject to limitsOnlyInCombat
        boolean applyInvLimits = true;
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUUID();
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

    public static boolean isItemLimitExceeded(ServerPlayer player, String itemId, int incomingCount) {
        // World limits ALWAYS enforced
        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current + incomingCount > wli.maxCount) {
                return true;
            }
            if (!DataManager.isOwner(player.getUUID(), itemId)) {
                if (!DataManager.tryAcquire(player.getUUID(), itemId, wli.maxCount)) {
                    return true;
                }
            }
        }

        // Inventory limits subject to limitsOnlyInCombat
        boolean applyInvLimits = true;
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUUID();
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
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) return false;

        var configLimits = ConfigManager.getConfig().enchantLimits;
        if (configLimits == null || configLimits.isEmpty()) return false;

        net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);
        boolean changed = false;

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> entry : enchantments.entrySet()) {
            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();

            net.minecraft.resources.Identifier id = enchantment.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            if (id != null) {
                String enchantId = id.toString();
                Integer limit = configLimits.get(enchantId);
                if (limit != null) {
                    if (limit == 0) {
                        mutable.set(enchantment, 0);
                        changed = true;
                    } else if (level > limit) {
                        mutable.set(enchantment, limit);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
        }
        return changed;
    }

    public static void checkAndEnforceItemLimits(ServerPlayer player) {
        if (player == null || player.getInventory() == null) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                sanitizeItemEnchantments(s);
            }
        }

        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUUID();
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

                for (int i = player.getInventory().getContainerSize() - 1; i >= 0; i--) {
                    if (excess <= 0) break;
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                        int count = stack.getCount();
                        int toDrop = Math.min(count, excess);
                        ItemStack dropStack = stack.split(toDrop);
                        player.drop(dropStack, false);
                        excess -= toDrop;
                    }
                }

                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            }
        }
    }

    public static void handleKill(ServerPlayer killer, ServerPlayer victim) {
        if (!ConfigManager.getConfig().rankedSystemEnabled) return;

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

                if (serverInstance != null) {
                    serverInstance.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(
                        Component.literal(killer.getName().getString() + " (now Rank #" + victimRank + ") killed " +
                                     victim.getName().getString() + " and took their rank!").withStyle(ChatFormatting.GOLD)
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
                    serverInstance.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(
                        Component.literal(killer.getName().getString() + " achieved Rank #" + finalNextRank + "!").withStyle(ChatFormatting.GOLD)
                    ));
                }

                updatePlayerNametag(killer);
            }
        }
    }

    @Override
    public void onInitialize() {
        System.out.println("[Combat] Mod initialized!");

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(com.combat.network.LungeBlockPayload.TYPE, com.combat.network.LungeBlockPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.combat.network.LungeBlockPayload.TYPE, (payload, context) -> {
            spearLungeExpiration.put(context.player().getUUID(), payload.timestamp());
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            File configDir = new File(server.getServerDirectory().toFile(), "config/combat");
            ConfigManager.init(configDir);
            DataManager.init(configDir);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            DataManager.onPlayerLogin(player.getUUID());
            assignRankIfUnranked(player);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                UUID uuid = player.getUUID();
                Long combatEnd = combatTagExpiration.get(uuid);
                if (combatEnd != null && System.currentTimeMillis() < combatEnd) {
                    combatTagExpiration.remove(uuid);
                    player.getInventory().dropAll();
                    UUID attackerUuid = lastAttackerMap.get(uuid);
                    if (attackerUuid != null && serverInstance != null) {
                        ServerPlayer attacker = serverInstance.getPlayerList().getPlayer(attackerUuid);
                        if (attacker != null) {
                            handleKill(attacker, player);
                        }
                    }
                    player.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().genericKill(), 1000.0F);
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[CombatLog] " + player.getName().getString() + " logged out during combat and was killed!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
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
            if (ubijalec instanceof ServerPlayer killerPlayer && zrtev instanceof ServerPlayer victimPlayer) {
                pendingPvPRespawn.add(victimPlayer.getUUID());
                handleKill(killerPlayer, victimPlayer);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer victim) {
                UUID victimUuid = victim.getUUID();
                Long combatEnd = combatTagExpiration.get(victimUuid);
                boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;

                if (inCombat) {
                    UUID attackerUuid = lastAttackerMap.get(victimUuid);
                    if (attackerUuid != null && serverInstance != null) {
                        ServerPlayer killer = serverInstance.getPlayerList().getPlayer(attackerUuid);
                        if (killer != null && killer != victim) {
                            handleKill(killer, victim);
                        }
                    }
                }
                combatTagExpiration.remove(victimUuid);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((stariIgralec, noviIgralec, alive) -> {
            UUID id = noviIgralec.getUUID();
            combatTagExpiration.remove(id);
            if (pendingPvPRespawn.contains(id)) {
                pendingPvPRespawn.remove(id);
                int immunitySeconds = ConfigManager.getConfig().immunitySeconds;
                if (immunitySeconds > 0) {
                    long immunityEnd = System.currentTimeMillis() + immunitySeconds * 1000L;
                    immunityExpiration.put(id, immunityEnd);
                    noviIgralec.sendSystemMessage(Component.literal("You have " + immunitySeconds + " seconds of protection because a player killed you!").withStyle(ChatFormatting.GREEN));
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victim) {
                if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
                    lastAttackerMap.put(victim.getUUID(), attacker.getUUID());
                    int combatSec = ConfigManager.getConfig().combatLogSeconds;
                    if (combatSec > 0) {
                        long combatEnd = System.currentTimeMillis() + combatSec * 1000L;
                        combatTagExpiration.put(victim.getUUID(), combatEnd);
                        combatTagExpiration.put(attacker.getUUID(), combatEnd);
                    }
                }
                UUID id = victim.getUUID();
                Long exp = immunityExpiration.get(id);
                if (exp != null) {
                    if (System.currentTimeMillis() < exp) {
                        return false;
                    } else {
                        immunityExpiration.remove(id);
                        victim.sendSystemMessage(Component.literal("Your protection has expired!").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            return true;
        });

        // LEFT CLICK ATTACK INTERCEPTION
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                if (entity instanceof ServerPlayer victimPlayer) {
                    lastAttackerMap.put(victimPlayer.getUUID(), serverPlayer.getUUID());
                    int combatSec = ConfigManager.getConfig().combatLogSeconds;
                    if (combatSec > 0) {
                        long combatEnd = System.currentTimeMillis() + combatSec * 1000L;
                        combatTagExpiration.put(serverPlayer.getUUID(), combatEnd);
                        combatTagExpiration.put(victimPlayer.getUUID(), combatEnd);
                    }
                }
                ItemStack mainHand = serverPlayer.getItemInHand(hand);
                String itemId = BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString();

                // Mace Cooldown: ONLY on smash attacks against PLAYERS
                // We schedule the cooldown for next tick so the smash actually fires first.
                if (mainHand.is(net.minecraft.world.item.Items.MACE) && entity instanceof ServerPlayer) {
                    if (serverPlayer.fallDistance > 1.5F) {
                        Double maceSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:mace");
                        if (maceSec == null || maceSec <= 0.0) maceSec = 5.0;
                        if (serverPlayer.getCooldowns().isOnCooldown(mainHand)) {
                            // Already on cooldown – block the smash
                            return net.minecraft.world.InteractionResult.FAIL;
                        }
                        // Schedule cooldown for end-of-tick so Minecraft can finish the smash first
                        pendingMaceCooldown.put(serverPlayer.getUUID(), (int)(maceSec * 20));
                    }
                }



            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // BLOCK INTERCEPTION (Ender Chest)
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                net.minecraft.world.level.block.state.BlockState state = world.getBlockState(hitResult.getBlockPos());
                if (state.is(net.minecraft.world.level.block.Blocks.ENDER_CHEST)) {
                    UUID uuid = serverPlayer.getUUID();
                    Long combatEnd = combatTagExpiration.get(uuid);
                    boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
                    if (inCombat && !ConfigManager.getConfig().allowEnderchestInCombat) {
                        serverPlayer.sendSystemMessage(Component.literal("Opening Ender Chest is disabled during combat!").withStyle(ChatFormatting.RED));
                        return net.minecraft.world.InteractionResult.FAIL;
                    }
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // RIGHT CLICK USE INTERCEPTION (Elytra, Fireworks, Ender Pearls)
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                UUID uuid = serverPlayer.getUUID();
                long now = System.currentTimeMillis();
                Long combatEnd = combatTagExpiration.get(uuid);
                boolean inCombat = combatEnd != null && now < combatEnd;

                if (inCombat) {
                    if (!ConfigManager.getConfig().allowElytraInCombat && stack.is(net.minecraft.world.item.Items.ELYTRA)) {
                        serverPlayer.sendSystemMessage(Component.literal("Equipping Elytra is disabled during combat!").withStyle(ChatFormatting.RED));
                        return net.minecraft.world.InteractionResult.FAIL;
                    }
                    if (!ConfigManager.getConfig().allowFireworksInCombat && stack.is(net.minecraft.world.item.Items.FIREWORK_ROCKET)) {
                        serverPlayer.sendSystemMessage(Component.literal("Firework Rockets are disabled during combat!").withStyle(ChatFormatting.RED));
                        return net.minecraft.world.InteractionResult.FAIL;
                    }
                }

                if ("minecraft:ender_pearl".equals(itemId)) {
                    if (inCombat) {
                        Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:ender_pearl");
                        if (cooldownSec != null && cooldownSec > 0.0) {
                            if (!serverPlayer.getCooldowns().isOnCooldown(stack)) {
                                pendingPearlThrows.add(uuid);
                            }
                        }
                    }
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID uuid : pendingPearlThrows) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    ItemStack pearl = new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL);
                    if (player.getCooldowns().isOnCooldown(pearl)) {
                        Double pearlSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:ender_pearl");
                        if (pearlSec != null && pearlSec > 0.0) {
                            player.getCooldowns().addCooldown(pearl, (int)(pearlSec * 20));
                        }
                    }
                }
            }
            pendingPearlThrows.clear();

            // Apply mace cooldowns scheduled from this tick's smash attacks
            for (Map.Entry<UUID, Integer> entry : pendingMaceCooldown.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    p.getCooldowns().addCooldown(new ItemStack(net.minecraft.world.item.Items.MACE), entry.getValue());
                }
            }
            pendingMaceCooldown.clear();

            for (Map.Entry<UUID, Long> entry : pendingLungeCooldown.entrySet()) {
                spearLungeExpiration.put(entry.getKey(), entry.getValue());
            }
            pendingLungeCooldown.clear();


        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("combat")
                .then(Commands.literal("rank")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        PlayerData data = DataManager.getOrCreatePlayerData(player.getUUID());
                        if (data.rankPosition == -1) {
                            context.getSource().sendSystemMessage(Component.literal("Your Rank: Unranked").withStyle(ChatFormatting.YELLOW));
                        } else {
                            int pos = data.rankPosition;
                            int extraHearts = Math.max(0, 11 - pos);
                            context.getSource().sendSystemMessage(Component.literal("Your Rank: Rank #" + pos + " (+" + extraHearts + " Extra Hearts)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        }
                        return 1;
                    })
                    .then(Commands.literal("top")
                        .executes(context -> {
                            java.util.List<Map.Entry<UUID, PlayerData>> rankedList = new java.util.ArrayList<>();
                            for (Map.Entry<UUID, PlayerData> entry : DataManager.getAllPlayersData().entrySet()) {
                                if (entry.getValue().rankPosition > 0) {
                                    rankedList.add(entry);
                                }
                            }
                            rankedList.sort(java.util.Comparator.comparingInt(e -> e.getValue().rankPosition));

                            context.getSource().sendSystemMessage(Component.literal("=== Top 20 Ranked Players ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                            if (rankedList.isEmpty()) {
                                context.getSource().sendSystemMessage(Component.literal("No ranked players found.").withStyle(ChatFormatting.GRAY));
                            } else {
                                int limit = Math.min(20, rankedList.size());
                                for (int i = 0; i < limit; i++) {
                                    Map.Entry<UUID, PlayerData> entry = rankedList.get(i);
                                    UUID uuid = entry.getKey();
                                    PlayerData pd = entry.getValue();
                                    ServerPlayer p = serverInstance != null ? serverInstance.getPlayerList().getPlayer(uuid) : null;
                                    String name = p != null ? p.getName().getString() : (pd.playerName != null && !pd.playerName.isEmpty() ? pd.playerName : uuid.toString().substring(0, 8));
                                    context.getSource().sendSystemMessage(Component.literal((i + 1) + ". " + name + " - Rank #" + pd.rankPosition).withStyle(ChatFormatting.YELLOW));
                                }
                            }
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("menu")
                    .requires(source -> source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS)))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        new com.combat.gui.CombatMenuGui(player).open();
                        return 1;
                    })
                )
            );
        });
    }
}
