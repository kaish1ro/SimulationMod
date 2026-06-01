package com.eternity.simulation.command;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.entity.RiftEntity;
import com.eternity.simulation.entity.RiftManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /simrift — тест-команды системы разломов.
 *
 * <ul>
 *   <li>{@code spawn [red|purple|blue|elite]} — мгновенный спавн разлома рядом с игроком</li>
 *   <li>{@code setnext <ticks>}               — следующий разлом через N тиков (0 = почти сразу);
 *       обходит требование убийства дракона, поэтому работает для первого разлома</li>
 *   <li>{@code reset}                         — сброс менеджера (nextRiftAt, pendingRift)</li>
 *   <li>{@code status}                        — полный дамп состояния</li>
 * </ul>
 */
public class RiftCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simrift")
            .requires(src -> src.hasPermission(2))

            // ── spawn [type] ──────────────────────────────────────────────────
            .then(Commands.literal("spawn")
                .executes(ctx -> doSpawn(ctx.getSource(), "red"))
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (RiftEntity.RiftType t : RiftEntity.RiftType.values())
                            builder.suggest(t.name().toLowerCase());
                        return builder.buildFuture();
                    })
                    .executes(ctx -> doSpawn(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "type")))
                )
            )

            // ── setnext <ticks> ───────────────────────────────────────────────
            .then(Commands.literal("setnext")
                .then(Commands.argument("ticks", LongArgumentType.longArg(0))
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        long ticks = LongArgumentType.getLong(ctx, "ticks");
                        ServerLevel level = src.getLevel();
                        long now = level.getGameTime();

                        SimulationSavedData data = SimulationSavedData.get(level);

                        // Обходим требование убийства дракона
                        if (!data.isDragonDefeated()) {
                            data.setDragonDefeated(true);
                        }
                        if (data.getDragonDefeatedAt() == 0) {
                            data.setDragonDefeatedAt(now);
                        }

                        data.clearPendingRift();
                        data.setNextRiftScheduledAt(ticks == 0 ? now : now + ticks);

                        long secs = ticks / 20;
                        src.sendSystemMessage(Component.literal(
                                "§a[simrift] §fСледующий разлом через §e" + ticks
                                + " §7тиков §f(≈ §e" + secs + "s§f)."));
                        return 1;
                    })
                )
            )

            // ── reset ─────────────────────────────────────────────────────────
            .then(Commands.literal("reset")
                .executes(ctx -> {
                    RiftManager.INSTANCE.resetState();
                    ctx.getSource().sendSystemMessage(Component.literal(
                            "§a[simrift] §fМенеджер разломов сброшен."));
                    return 1;
                })
            )

            // ── status ────────────────────────────────────────────────────────
            .then(Commands.literal("status")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel();
                    SimulationSavedData data = SimulationSavedData.get(level);
                    long now   = level.getGameTime();
                    long nextAt = data.getNextRiftScheduledAt();

                    src.sendSystemMessage(Component.literal("§6══ simrift status ══"));
                    src.sendSystemMessage(Component.literal(
                            "§7  dragonDefeated: §f" + data.isDragonDefeated()
                            + "  §7defeatedAt: §f" + data.getDragonDefeatedAt()));
                    src.sendSystemMessage(Component.literal(
                            "§7  elites: §f" + data.getRiftEliteCount()
                            + "  §7normalInCycle: §f" + data.getRiftNormalInCycle()
                            + "§7/§f" + RiftManager.normalNeeded(data)));

                    String nextInfo;
                    if (nextAt == -1) {
                        nextInfo = "§cне запланировано";
                    } else {
                        long diff = nextAt - now;
                        nextInfo = "§eтик " + nextAt + " §7(через §e" + diff / 20 + "s§7)";
                    }
                    src.sendSystemMessage(Component.literal("§7  next: " + nextInfo));

                    String pendingInfo;
                    if (data.hasPendingRift()) {
                        RiftEntity.RiftType t = RiftEntity.RiftType.fromId((byte) data.getPendingRiftType());
                        pendingInfo = "§e" + t + " §7@ §f("
                                + (int) data.getPendingRiftX() + ", "
                                + (int) data.getPendingRiftY() + ", "
                                + (int) data.getPendingRiftZ() + ")";
                    } else {
                        pendingInfo = "§7нет";
                    }
                    src.sendSystemMessage(Component.literal("§7  pending: " + pendingInfo));
                    src.sendSystemMessage(Component.literal(
                            "§7  activeUUID: §f" + RiftManager.INSTANCE.getActiveRiftUUID()));
                    return 1;
                })
            )
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int doSpawn(CommandSourceStack src, String typeName) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("§cЭта команда только для игроков."));
            return 0;
        }

        RiftEntity.RiftType type;
        try {
            type = RiftEntity.RiftType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal(
                    "§cНеверный тип: §e" + typeName
                    + "§c. Допустимые: red, purple, blue, elite."));
            return 0;
        }

        boolean ok = RiftManager.INSTANCE.spawnRift(player, player.serverLevel(), type);
        if (ok) {
            src.sendSystemMessage(Component.literal(
                    "§a[simrift] §fСоздан §e" + type.name() + " §fразлом."));
        } else {
            src.sendFailure(Component.literal("§cНе удалось создать разлом (аддEntity вернул false)."));
        }
        return ok ? 1 : 0;
    }
}
