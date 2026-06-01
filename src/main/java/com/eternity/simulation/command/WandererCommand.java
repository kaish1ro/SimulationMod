package com.eternity.simulation.command;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.ModEvents;
import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.entity.EncounterManager;
import com.eternity.simulation.entity.SpawnHelper;
import com.eternity.simulation.entity.WandererEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * /simwander — команды для тестирования системы встреч со Скитальцем.
 *
 * <ul>
 *   <li>{@code spawn}         — мгновенно спавнит Скитальца рядом (обходит все условия)</li>
 *   <li>{@code reset}         — полный сброс: флаг Ада, таймер, активная встреча</li>
 *   <li>{@code trigger}       — симулирует первый выход из Ада</li>
 *   <li>{@code status}        — показывает текущее состояние</li>
 *   <li>{@code scene spawn}   — принудительно спавнит Скитальца сцены (монолог)</li>
 *   <li>{@code scene reset}   — сбрасывает exitSceneTriggered, ставит dragonDefeated=true</li>
 * </ul>
 */
public class WandererCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simwander")
            .requires(src -> src.hasPermission(2))  // только ops

            // ── spawn ─────────────────────────────────────────────────────────
            .then(Commands.literal("spawn")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();
                    ServerLevel level = src.getLevel();

                    BlockPos pos = findTestSpawnPos(player, level);
                    if (pos == null) {
                        src.sendFailure(Component.literal("§cНе удалось найти позицию для спавна."));
                        return 0;
                    }

                    WandererEntity wanderer = ModEntities.WANDERER.get().create(level);
                    if (wanderer == null) {
                        src.sendFailure(Component.literal("§cОшибка создания сущности."));
                        return 0;
                    }

                    wanderer.setTargetPlayer(player.getUUID());
                    wanderer.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
                    level.addFreshEntity(wanderer);

                    // Регистрируем в менеджере чтобы он знал об активной встрече
                    EncounterManager.INSTANCE.registerTestWanderer(wanderer.getUUID());

                    src.sendSystemMessage(Component.literal(
                        "§a[simwander] §fСкиталец заспавнен на " + pos + ". Идёт к тебе."));
                    return 1;
                })
            )

            // ── reset ─────────────────────────────────────────────────────────
            .then(Commands.literal("reset")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel();

                    SimulationSavedData data = SimulationSavedData.get(level);
                    data.setHasEnteredNether(false);

                    EncounterManager.INSTANCE.resetState();

                    src.sendSystemMessage(Component.literal(
                        "§a[simwander] §fСброшено: §ehasEnteredNether=false§f, таймер и активная встреча очищены."));
                    return 1;
                })
            )

            // ── trigger ───────────────────────────────────────────────────────
            // Симулирует «игрок вышел из Ада» — ставит флаг и планирует встречу
            .then(Commands.literal("trigger")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer player = src.getPlayerOrException();

                    EncounterManager.INSTANCE.onPlayerExitedNether(player);

                    src.sendSystemMessage(Component.literal(
                        "§a[simwander] §fТриггер выхода из Ада сработал. Встреча запланирована через 1-3 мин."));
                    return 1;
                })
            )

            // ── status ────────────────────────────────────────────────────────
            .then(Commands.literal("status")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel();

                    SimulationSavedData data = SimulationSavedData.get(level);
                    long now = level.getGameTime();

                    String status = EncounterManager.INSTANCE.getStatusString(now);

                    src.sendSystemMessage(Component.literal("§e[simwander] §fСостояние встреч:"));
                    src.sendSystemMessage(Component.literal("§7  hasEnteredNether:    §f" + data.hasEnteredNether()));
                    src.sendSystemMessage(Component.literal("§7  dragonDefeated:      §f" + data.isDragonDefeated()));
                    src.sendSystemMessage(Component.literal("§7  exitSceneTriggered:  §f" + data.isExitSceneTriggered()));
                    src.sendSystemMessage(Component.literal(status));
                    return 1;
                })
            )

            // ── scene ─────────────────────────────────────────────────────────
            .then(Commands.literal("scene")

                // /simwander scene reset  →  сбросить флаги сцены выхода из Края
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        ServerLevel level = src.getLevel();

                        SimulationSavedData data = SimulationSavedData.get(level);
                        data.setExitSceneTriggered(false);
                        data.setDragonDefeated(true);  // чтобы следующий выход сработал

                        src.sendSystemMessage(Component.literal(
                            "§a[simwander] §fСцена Края сброшена: " +
                            "§eexitSceneTriggered=false§f, §edragonDefeated=true§f."));
                        return 1;
                    })
                )

                // /simwander scene spawn  →  принудительно запустить сцену прямо сейчас
                .then(Commands.literal("spawn")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        ServerPlayer player = src.getPlayerOrException();

                        // forceScene=true — игнорирует флаги dragonDefeated и exitSceneTriggered
                        ModEvents.spawnEndExitScene(player, true);

                        src.sendSystemMessage(Component.literal(
                            "§a[simwander] §fСкиталец сцены заспавнен. " +
                            "Жди ~3 секунды пока он подойдёт."));
                        return 1;
                    })
                )
            )
        );
    }

    /**
     * Ищет позицию для тестового спавна: сначала через SpawnHelper,
     * при неудаче — 8 блоков прямо за спиной игрока.
     */
    private static BlockPos findTestSpawnPos(ServerPlayer player, ServerLevel level) {
        // Пробуем нормальный спавн
        BlockPos pos = SpawnHelper.isUnderground(player)
            ? SpawnHelper.findUnderground(player, level)
            : SpawnHelper.findSurface(player, level);

        if (pos != null) return pos;

        // Фоллбэк — прямо за спиной, 8 блоков
        double yaw = Math.toRadians(player.getYRot() + 180);
        int x = (int)(player.getX() + Math.cos(yaw) * 8);
        int z = (int)(player.getZ() + Math.sin(yaw) * 8);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        return new BlockPos(x, y, z);
    }
}
