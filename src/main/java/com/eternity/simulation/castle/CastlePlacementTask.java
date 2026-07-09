package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Однократная установка castle.nbt и labyrinth.nbt поверх зачищенного канонического
 * Final Castle, относительно castleAnchorPos. Собирает DATA-маркеры из обеих структур
 * и сохраняет их в {@link SimulationSavedData} для дальнейшей обработки (спавн мобов/лута).
 */
public final class CastlePlacementTask {

    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("simulation.CastlePlacement");

    private CastlePlacementTask() {}

    public static boolean run(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        List<CastleDataMarker> allMarkers = new ArrayList<>();

        if (!placeCastlePart(level, anchor, allMarkers, requester)) return false;
        if (!placeLabyrinthPart(level, anchor, allMarkers, requester)) return false;

        finalizeBuild(level, allMarkers, requester);
        return true;
    }

    /**
     * Ставит основной корпус замка + низ синей башни. Вынесено отдельно от
     * лабиринта, чтобы {@link com.eternity.simulation.castle.CastleInterceptTask}
     * могло вклинить {@code /simcastle towers} МЕЖДУ постройкой корпуса и лабиринта
     * (лестницу в лабиринт достраивает towers, поэтому лабиринт должен появиться позже).
     */
    public static boolean placeCastlePart(ServerLevel level, BlockPos anchor,
                                           List<CastleDataMarker> outMarkers, ServerPlayer requester) {
        if (!placeOne(level, anchor, CastleStructures.CASTLE, CastleConstants.CASTLE_OFFSET, outMarkers, requester)) {
            return false;
        }

        int restoredPaintings = CastlePaintingRestoreTask.restore(level);
        if (restoredPaintings > 0 && requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§a[simcastle] §7Восстановлено записей реестра картин Immersive Paintings: §f" + restoredPaintings));
        }

        return placeOne(level, anchor, CastleStructures.BLUE_TOWER_BOTTOM, CastleConstants.BLUE_TOWER_BOTTOM_OFFSET, outMarkers, requester);
    }

    /** Ставит лабиринт — см. {@link #placeCastlePart}. */
    public static boolean placeLabyrinthPart(ServerLevel level, BlockPos anchor,
                                              List<CastleDataMarker> outMarkers, ServerPlayer requester) {
        return placeOne(level, anchor, CastleStructures.LABYRINTH, CastleConstants.LABYRINTH_OFFSET, outMarkers, requester);
    }

    /**
     * Финализирует установку: сохраняет маркеры, инициализирует спавн-систему,
     * сбрасывает квестовую цепочку и ставит сундуки с лутом. Вызывается один раз,
     * когда ВСЕ маркеры (корпус + лабиринт) уже собраны.
     */
    public static void finalizeBuild(ServerLevel level, List<CastleDataMarker> allMarkers, ServerPlayer requester) {
        SimulationSavedData data = SimulationSavedData.get(level.getServer().overworld());
        data.setCastleMarkers(allMarkers);
        data.initCastleSpawnSystem(CastleSpawnDefinition.fromMarkers(allMarkers));

        // Перестройка замка = перезапуск его квестовой цепочки: без сброса
        // квесты/подзадания остались бы выполненными с прошлого прохождения
        // и вся прогрессия (чек-пойнты → ключи → этажи) молча не работала бы.
        com.eternity.simulation.quests.SimulationQuestState.get(level.getServer().overworld())
            .resetQuests(List.of("labyrinth_exit", "floor1_inspect", "blue_tower_inspect",
                "castle_exit_search", "outer_towers_inspect", "yellow_towers_enter", "floor2_inspect",
                "roof_inspect", "shield_removal", "castle_escape"));
        com.eternity.simulation.quests.SubQuestState.get(level.getServer().overworld()).resetAll();
        com.eternity.simulation.quests.QuestSync.syncMainQuests(level.getServer());
        com.eternity.simulation.quests.QuestSync.syncSubQuests(level.getServer());

        int chestCount = placeLootChests(level, allMarkers);

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§a[simcastle] §7Установка завершена. Маркеров найдено: §f" + allMarkers.size()
                    + "§7, сундуков с лутом: §f" + chestCount));
        }
    }

    /**
     * Устанавливает castle_roof.nbt (крыша главного корпуса) — отдельным шагом,
     * после победы над {@code floor1_boss} (пока вызывается командой вручную).
     * Дополняет общий список маркеров, не трогая уже инициализированную спавн-систему.
     */
    public static boolean runRoof(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        SimulationSavedData preData = SimulationSavedData.get(level.getServer().overworld());

        // Защита от повторной установки: крыша уже стоит (есть beacon_particle_effect маркер)
        boolean alreadyPlaced = preData.getCastleMarkers().stream()
            .anyMatch(m -> m.has("beacon_particle_effect"));
        if (alreadyPlaced) {
            LOGGER.info("[runRoof] Крыша уже установлена — повторная установка пропущена, только (пере)запуск задачи");
            CastleRoofSealTask.start(level, preData, anchor);
            return true;
        }

        List<CastleDataMarker> roofMarkers = new ArrayList<>();

        if (!placeOne(level, anchor, CastleStructures.CASTLE_ROOF, CastleConstants.CASTLE_ROOF_OFFSET, roofMarkers, requester)) {
            return false;
        }

        SimulationSavedData data = SimulationSavedData.get(level.getServer().overworld());
        if (!roofMarkers.isEmpty()) {
            List<CastleDataMarker> allMarkers = new ArrayList<>(data.getCastleMarkers());
            allMarkers.addAll(roofMarkers);
            data.setCastleMarkers(allMarkers);
            List<CastleSpawnDefinition> allDefs = CastleSpawnDefinition.fromMarkers(allMarkers);
            data.extendCastleSpawnSystem(allDefs);
        }

        CastleRoofSealTask.start(level, data, anchor);

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§a[simcastle] §7Крыша установлена. Маркеров найдено: §f" + roofMarkers.size()));
        }
        return true;
    }

    /**
     * Назначает лут-таблицу {@code loot_table=...} существующему сундуку под маркером
     * (маркер стоял на блок выше пола, сундук вручную поставлен под ним).
     */
    private static int placeLootChests(ServerLevel level, List<CastleDataMarker> markers) {
        int placed = 0;
        for (CastleDataMarker marker : markers) {
            if (!marker.has("loot_table")) continue;

            String lootTable = marker.get("loot_table");
            if ("poor_chest_3_blocks_down".equals(lootTable)) {
                for (int depth = 1; depth <= 3; depth++) {
                    if (assignLootTable(level, marker.pos().below(depth), "poor_chest_meager")) {
                        placed++;
                    }
                }
                continue;
            }

            if (assignLootTable(level, marker.pos().below(), lootTable)) {
                placed++;
            }
        }
        return placed;
    }

    private static boolean assignLootTable(ServerLevel level, BlockPos pos, String lootTable) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(new ResourceLocation("simulation", "chests/" + lootTable), level.random.nextLong());
            return true;
        }
        return false;
    }

    private static boolean placeOne(ServerLevel level, BlockPos anchor, ResourceLocation structureId,
                                     BlockPos offset, List<CastleDataMarker> out, ServerPlayer requester) {
        BlockPos placePos = anchor.offset(offset);
        Optional<List<CastleDataMarker>> result = CastleStructures.placeAndCollectMarkers(level, structureId, placePos);

        if (result.isEmpty()) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§c[simcastle] §7Структура §f" + structureId
                        + " §7не найдена (сохрани её structure-блоком с этим именем)."));
            }
            return false;
        }

        out.addAll(result.get());
        return true;
    }
}
