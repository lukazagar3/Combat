package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class CombatMod implements ModInitializer {
    public static MinecraftServer serverInstance;
    public static final Map<UUID, Long> combatTagExpiration = new HashMap<>();
    public static final Map<UUID, Long> immunityExpiration = new HashMap<>();
    public static final Map<UUID, Long> tridentRiptideExpiration = new HashMap<>();
    public static final Map<UUID, Long> spearLungeExpiration = new HashMap<>();
    public static final java.util.Set<String> notifiedOwnership = new java.util.HashSet<>();

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

        // 1. Check Inventory Limit
        Integer invLimit = ConfigManager.getConfig().itemLimits.get(itemId);
        if (invLimit != null && invLimit >= 0) {
            int current = countItemInPlayer(player, itemId);
            if (current + incomingCount > invLimit) {
                return true;
            }
        }

        // 2. Check World Limit
        CombatConfig.WorldLimitedItem wli = ConfigManager.getConfig().worldLimits.get(itemId);
        if (wli != null && wli.maxCount > 0) {
            int current = countItemInPlayer(player, itemId);
            if (current > 0) {
                return true; // Already carrying one or more, cannot acquire extra!
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
                        player.dropItem(dropStack, false); // Drop on ground, NEVER destroy!
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

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();

                if ("minecraft:spear".equals(itemId) || "minecraft:netherite_spear".equals(itemId)) {
                    Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:spear");
                    if (cooldownSec != null && cooldownSec > 0.0) {
                        UUID uuid = serverPlayer.getUuid();
                        long now = System.currentTimeMillis();
                        Long expiration = spearLungeExpiration.get(uuid);
                        if (expiration != null && now < expiration) {
                            double remaining = (expiration - now) / 1000.0;
                            serverPlayer.sendMessage(Text.literal(String.format("Spear Lunge is on cooldown for %.1fs!", remaining)).formatted(Formatting.RED), false);
                            serverPlayer.getItemCooldownManager().set(stack, (int)(remaining * 20));
                            return net.minecraft.util.ActionResult.FAIL;
                        }
                        spearLungeExpiration.put(uuid, now + (long)(cooldownSec * 1000L));
                        serverPlayer.getItemCooldownManager().set(stack, (int)(cooldownSec * 20));
                    }
                } else if ("minecraft:ender_pearl".equals(itemId)) {
                    Double cooldownSec = ConfigManager.getConfig().itemCooldowns.get("minecraft:ender_pearl");
                    if (cooldownSec != null && cooldownSec > 0.0) {
                        serverPlayer.getItemCooldownManager().set(stack, (int)(cooldownSec * 20));
                    }
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Command registrations
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("combat")
                .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS))) // Operator / Admin
                .then(CommandManager.literal("menu")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        new com.combat.gui.CombatMenuGui(player).open();
                        return 1;
                    })
                )
                .then(CommandManager.literal("limit")
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    Identifier itemId = ItemStackArgumentType.getItemStackArgument(context, "item").getItem().getRegistryEntry().registryKey().getValue();
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    ConfigManager.getConfig().itemLimits.put(itemId.toString(), amount);
                                    ConfigManager.save();
                                    context.getSource().sendFeedback(() -> Text.literal("Set inventory limit of " + itemId + " to " + amount), true);
                                    return 1;
                                })
                            )
                        )
                    )
                )
            );

            dispatcher.register(CommandManager.literal("cooldown")
                .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                        .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.0))
                            .executes(context -> {
                                Identifier itemId = ItemStackArgumentType.getItemStackArgument(context, "item").getItem().getRegistryEntry().registryKey().getValue();
                                double seconds = DoubleArgumentType.getDouble(context, "seconds");
                                ConfigManager.getConfig().itemCooldowns.put(itemId.toString(), seconds);
                                ConfigManager.save();
                                context.getSource().sendFeedback(() -> Text.literal("Set cooldown of " + itemId + " to " + seconds + "s"), true);
                                return 1;
                            })
                        )
                    )
                )
            );

            dispatcher.register(CommandManager.literal("combat_log")
                .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                            ConfigManager.getConfig().combatLogSeconds = seconds;
                            ConfigManager.save();
                            context.getSource().sendFeedback(() -> Text.literal("Set combat log timer to " + seconds + "s"), true);
                            return 1;
                        })
                    )
                )
            );
        });

        // Ticking player session playtime
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            Set<UUID> onlineUuids = new HashSet<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                onlineUuids.add(uuid);
                DataManager.tickOnlinePlayer(uuid);
            }
            // Periodically check and update world limit owners (every 20 ticks = 1 second)
            if (server.getTicks() % 20 == 0) {
                DataManager.updateWorldLimits(now, ConfigManager.getConfig().worldLimits.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(e -> e.getKey(), e -> e.getValue().maxCount)), onlineUuids);
            }
        });

        // Connection events
        // Connection events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DataManager.onPlayerLogin(handler.getPlayer().getUuid());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
            if (combatTagExpiration.containsKey(uuid)) {
                combatTagExpiration.remove(uuid);
                // Drop all items on the ground so combat loggers drop everything
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        player.dropItem(stack.copy(), true, false);
                        player.getInventory().setStack(i, ItemStack.EMPTY);
                    }
                }
                player.kill(player.getCommandSource().getWorld());
            }
            DataManager.onPlayerLogout(uuid);
        });

        // Block opening Ender Chest in combat if disabled
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                if (world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.ENDER_CHEST)) {
                    if (combatTagExpiration.containsKey(serverPlayer.getUuid())) {
                        if (!ConfigManager.getConfig().allowEnderchestInCombat) {
                            serverPlayer.sendMessage(Text.literal("You cannot open Ender Chests in combat!").formatted(Formatting.RED), false);
                            return ActionResult.FAIL;
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });
    }
}
