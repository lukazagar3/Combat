package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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

            player.sendMessage(Text.literal("You joined the server and received Rank #" + nextRank + "!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            updatePlayerNametag(player);
        }
    }

    public static void updatePlayerNametag(ServerPlayerEntity player) {
        MinecraftServer server = serverInstance;
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
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUuid();
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
            } else if (!DataManager.isOwner(player.getUuid(), itemId)) {
                if (!DataManager.tryAcquire(player.getUuid(), itemId, wli.maxCount)) {
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

    public static boolean isItemLimitExceeded(ServerPlayerEntity player, String itemId, int incomingCount) {
        if (ConfigManager.getConfig().limitsOnlyInCombat) {
            UUID uuid = player.getUuid();
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
            if (!DataManager.isOwner(player.getUuid(), itemId)) {
                if (!DataManager.tryAcquire(player.getUuid(), itemId, wli.maxCount)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void checkAndEnforceItemLimits(ServerPlayerEntity player) {
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
                        player.dropItem(dropStack, false, false);
                        excess -= toDrop;
                    }
                }

                if (player.currentScreenHandler != null) {
                    player.currentScreenHandler.sendContentUpdates();
                }
            }
        }
    }

    @Override
    public void onInitialize() {
        System.out.println("[Combat] Mod initialized!");
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
            DataManager.onPlayerLogout(player.getUuid());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack mainHand = serverPlayer.getStackInHand(hand);
                if (mainHand.isOf(net.minecraft.item.Items.MACE)) {
                    if (serverPlayer.getItemCooldownManager().isCoolingDown(mainHand)) {
                        return net.minecraft.util.ActionResult.FAIL;
                    }
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                UUID uuid = serverPlayer.getUuid();
                long now = System.currentTimeMillis();
                Long combatEnd = combatTagExpiration.get(uuid);
                boolean inCombat = combatEnd != null && now < combatEnd;

                if (inCombat) {
                    if (!ConfigManager.getConfig().allowElytraInCombat && stack.isOf(net.minecraft.item.Items.ELYTRA)) {
                        serverPlayer.sendMessage(Text.literal("Equipping Elytra is disabled during combat!").formatted(Formatting.RED), false);
                        return net.minecraft.util.ActionResult.FAIL;
                    }
                    if (!ConfigManager.getConfig().allowFireworksInCombat && stack.isOf(net.minecraft.item.Items.FIREWORK_ROCKET)) {
                        serverPlayer.sendMessage(Text.literal("Firework Rockets are disabled during combat!").formatted(Formatting.RED), false);
                        return net.minecraft.util.ActionResult.FAIL;
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
            return net.minecraft.util.ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID uuid : pendingPearlThrows) {
                net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    ItemStack pearl = new ItemStack(net.minecraft.item.Items.ENDER_PEARL);
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
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        PlayerData data = DataManager.getOrCreatePlayerData(player.getUuid());
                        if (data.rankPosition == -1) {
                            context.getSource().sendMessage(Text.literal("Your Rank: Unranked").formatted(Formatting.YELLOW));
                        } else {
                            int pos = data.rankPosition;
                            int extraHearts = Math.max(0, 11 - pos);
                            context.getSource().sendMessage(Text.literal("Your Rank: Rank #" + pos + " (+" + extraHearts + " Extra Hearts)").formatted(Formatting.GOLD, Formatting.BOLD));
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

                            context.getSource().sendMessage(Text.literal("=== Top 20 Ranked Players ===").formatted(Formatting.GOLD, Formatting.BOLD));
                            if (rankedList.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("No ranked players found.").formatted(Formatting.GRAY));
                            } else {
                                int limit = Math.min(20, rankedList.size());
                                for (int i = 0; i < limit; i++) {
                                    Map.Entry<UUID, PlayerData> entry = rankedList.get(i);
                                    UUID uuid = entry.getKey();
                                    PlayerData pd = entry.getValue();
                                    ServerPlayerEntity p = serverInstance != null ? serverInstance.getPlayerManager().getPlayer(uuid) : null;
                                    String name = p != null ? p.getName().getString() : (pd.playerName != null && !pd.playerName.isEmpty() ? pd.playerName : uuid.toString().substring(0, 8));
                                    context.getSource().sendMessage(Text.literal((i + 1) + ". " + name + " - Rank #" + pd.rankPosition).formatted(Formatting.YELLOW));
                                }
                            }
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("menu")
                    .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        new com.combat.gui.CombatMenuGui(player).open();
                        return 1;
                    })
                )
                .then(CommandManager.literal("limit")
                    .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    Identifier itemId = Registries.ITEM.getId(ItemStackArgumentType.getItemStackArgument(context, "item").getItem());
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    ConfigManager.getConfig().itemLimits.put(itemId.toString(), amount);
                                    ConfigManager.save();
                                    context.getSource().sendMessage(Text.literal("Set inventory limit of " + itemId + " to " + amount));
                                    return 1;
                                })
                            )
                        )
                    )
                )
            );
        });
    }
}
