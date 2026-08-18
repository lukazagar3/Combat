package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public class CombatMod implements ModInitializer {
    public static MinecraftServer serverInstance;
    public static final Map<UUID, Long> combatTagExpiration = new HashMap<>();
    public static final Map<UUID, Long> tridentRiptideExpiration = new HashMap<>();
    public static final java.util.Set<UUID> pendingPearlThrows = new java.util.HashSet<>();
    public static final java.util.Set<String> notifiedOwnership = new java.util.HashSet<>();

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
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUUID();
            Long combatEnd = combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
            if (!inCombat) {
                return Integer.MAX_VALUE;
            }
        }

        int maxAllowed = Integer.MAX_VALUE;

        Integer invLimit = ConfigManager.getConfig().itemLimits.get(itemId);
        if (invLimit != null && invLimit >= 0) {
            int current = countItemInPlayer(player, itemId);
            maxAllowed = Math.min(maxAllowed, Math.max(0, invLimit - current));
        }

        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current >= 1) {
                maxAllowed = 0;
            } else if (!DataManager.isOwner(player.getUUID(), itemId)) {
                if (!DataManager.tryAcquire(player.getUUID(), itemId, wli.maxCount)) {
                    maxAllowed = 0;
                } else {
                    maxAllowed = Math.min(maxAllowed, 1);
                }
            } else {
                maxAllowed = Math.min(maxAllowed, 1);
            }
        }

        return maxAllowed;
    }

    public static boolean isItemLimitExceeded(ServerPlayer player, String itemId, int incomingCount) {
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUUID();
            Long combatEnd = combatTagExpiration.get(uuid);
            boolean inCombat = combatEnd != null && System.currentTimeMillis() < combatEnd;
            if (!inCombat) {
                return false;
            }
        }

        Integer invLimit = ConfigManager.getConfig().itemLimits.get(itemId);
        if (invLimit != null && invLimit >= 0) {
            int current = countItemInPlayer(player, itemId);
            if (current + incomingCount > invLimit) {
                return true;
            }
        }

        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current > 0) {
                return true;
            }
            if (!DataManager.isOwner(player.getUUID(), itemId)) {
                if (!DataManager.tryAcquire(player.getUUID(), itemId, wli.maxCount)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void checkAndEnforceItemLimits(ServerPlayer player) {
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

    @Override
    public void onInitialize() {
        System.out.println("[Combat] Mod initialized!");
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
            DataManager.onPlayerLogout(player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ItemStack mainHand = serverPlayer.getItemInHand(hand);
                if (mainHand.is(net.minecraft.world.item.Items.MACE)) {
                    if (serverPlayer.getCooldowns().isOnCooldown(mainHand)) {
                        return net.minecraft.world.InteractionResult.FAIL;
                    }
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

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
                .then(Commands.literal("limit")
                    .requires(source -> source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS)))
                    .then(Commands.literal("set")
                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    ItemInput itemInput = ItemArgument.getItem(context, "item");
                                    Identifier itemId = BuiltInRegistries.ITEM.getKey(itemInput.item().value());
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    ConfigManager.getConfig().itemLimits.put(itemId.toString(), amount);
                                    ConfigManager.save();
                                    context.getSource().sendSystemMessage(Component.literal("Set inventory limit of " + itemId + " to " + amount));
                                    return 1;
                                })
                            )
                        )
                    )
                )
            );

            dispatcher.register(Commands.literal("cooldown")
                .requires(source -> source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("set")
                    .then(Commands.argument("item", ItemArgument.item(registryAccess))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.0))
                            .executes(context -> {
                                ItemInput itemInput = ItemArgument.getItem(context, "item");
                                Identifier itemId = BuiltInRegistries.ITEM.getKey(itemInput.item().value());
                                double seconds = DoubleArgumentType.getDouble(context, "seconds");
                                ConfigManager.getConfig().itemCooldowns.put(itemId.toString(), seconds);
                                ConfigManager.save();
                                context.getSource().sendSystemMessage(Component.literal("Set cooldown of " + itemId + " to " + seconds + "s"));
                                return 1;
                            })
                        )
                    )
                )
            );

            dispatcher.register(Commands.literal("combat_log")
                .requires(source -> source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("set")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            ConfigManager.getConfig().combatLogSeconds = seconds;
                            ConfigManager.save();
                            context.getSource().sendSystemMessage(Component.literal("Set combat log timer to " + seconds + "s"));
                            return 1;
                        })
                    )
                )
            );
        });
    }
}
