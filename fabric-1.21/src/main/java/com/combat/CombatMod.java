package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.DoubleArgumentType;
import net.minecraft.command.argument.IntegerArgumentType;
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

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CombatMod implements ModInitializer {
    public static MinecraftServer serverInstance;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            File configDir = new File(server.getRunDirectory(), "config/combat");
            ConfigManager.init(configDir);
            DataManager.init(configDir);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        // Command registrations
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("combat")
                .requires(source -> source.hasPermissionLevel(2)) // Operator / Admin
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
                .requires(source -> source.hasPermissionLevel(2))
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
                .requires(source -> source.hasPermissionLevel(2))
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
            if (com.combat.mixin.LivingEntityMixin.combatTagExpiration.containsKey(uuid)) {
                com.combat.mixin.LivingEntityMixin.combatTagExpiration.remove(uuid);
                player.kill();
            }
            DataManager.onPlayerLogout(uuid);
        });

        // Block opening Ender Chest in combat if disabled
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                if (world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.ENDER_CHEST)) {
                    if (com.combat.mixin.LivingEntityMixin.combatTagExpiration.containsKey(serverPlayer.getUuid())) {
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
