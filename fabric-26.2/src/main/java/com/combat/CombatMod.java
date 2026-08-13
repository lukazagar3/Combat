package com.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

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

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            serverInstance = server;
            File configDir = new File(server.getServerDirectory().toFile(), "config/combat");
            ConfigManager.init(configDir);
            DataManager.init(configDir);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DataManager.save();
            ConfigManager.save();
        });

        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if ("minecraft:spear".equals(itemId)) {
                    java.util.UUID uuid = serverPlayer.getUUID();
                    long now = System.currentTimeMillis();
                    Long combatEnd = combatTagExpiration.get(uuid);
                    if (combatEnd != null && now < combatEnd) {
                        serverPlayer.getCooldowns().addCooldown(stack, 1);
                    }
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("combat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("menu")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrThrow();
                        new com.combat.gui.CombatMenuGui(player).open();
                        return 1;
                    })
                )
                .then(Commands.literal("limit")
                    .then(Commands.literal("set")
                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    ItemInput itemInput = ItemArgument.getItem(context, "item");
                                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
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
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                    .then(Commands.argument("item", ItemArgument.item(registryAccess))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.0))
                            .executes(context -> {
                                ItemInput itemInput = ItemArgument.getItem(context, "item");
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
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
                .requires(source -> source.hasPermission(2))
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

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            Set<UUID> onlineUuids = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList()) {
                UUID uuid = player.getUUID();
                onlineUuids.add(uuid);
                DataManager.tickOnlinePlayer(uuid);
            }
            if (server.getTickCount() % 20 == 0) {
                DataManager.updateWorldLimits(now, ConfigManager.getConfig().worldLimits.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(e -> e.getKey(), e -> e.getValue().maxCount)), onlineUuids);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DataManager.onPlayerLogin(handler.getPlayer().getUUID());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            if (combatTagExpiration.containsKey(uuid)) {
                combatTagExpiration.remove(uuid);
                player.kill();
            }
            DataManager.onPlayerLogout(uuid);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayer serverPlayer) {
                if (world.getBlockState(hitResult.getBlockPos()).is(Blocks.ENDER_CHEST)) {
                    if (combatTagExpiration.containsKey(serverPlayer.getUUID())) {
                        if (!ConfigManager.getConfig().allowEnderchestInCombat) {
                            serverPlayer.sendSystemMessage(Component.literal("You cannot open Ender Chests in combat!").withStyle(ChatFormatting.RED));
                            return InteractionResult.FAIL;
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }
}
