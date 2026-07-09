package com.eternity.simulation.castle;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.quests.QuestSync;
import com.eternity.simulation.quests.SimulationQuestState;
import com.eternity.simulation.quests.SubQuestRegistry;
import com.eternity.simulation.quests.SubQuestState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Прогресс промежуточных заданий Финального замка — активация по чек-пойнтам
 * ({@link #tryActivate}, вызывается из {@link CastleSpawnPointTask}), по прямым
 * игровым событиям ({@link #onBlueTowerGuardianDefeated}, {@link #onHelvarDefeated},
 * {@link #onBeaconPlaced}) и периодическая проверка условий завершения
 * ({@link #tick}, раз в секунду).
 *
 * <p>Цепочка (id — {@link com.eternity.simulation.quests.SimulationQuestRegistry}):
 * <pre>
 * labyrinth_exit → floor1_inspect → blue_tower_inspect → castle_exit_search
 *   → outer_towers_inspect → yellow_towers_enter → floor2_inspect → roof_inspect → shield_removal → castle_escape
 * </pre>
 */
public final class CastleSubQuestTask {

    private static final Map<Integer, String> LABYRINTH_SPAWNPOINT_TO_DOOR = Map.of(
        2, "labyrinth_room_1",
        3, "labyrinth_room_2",
        4, "labyrinth_room_3"
    );
    private static final Map<String, String> DOOR_TO_SUBQUEST = Map.of(
        "labyrinth_room_1", SubQuestRegistry.KEY_LABYRINTH_ROOM_1,
        "labyrinth_room_2", SubQuestRegistry.KEY_LABYRINTH_ROOM_2,
        "labyrinth_room_3", SubQuestRegistry.KEY_LABYRINTH_ROOM_3
    );

    private static final int LABYRINTH_EXIT_SPAWNPOINT = 5;
    private static final int CASTLE_EXIT_SPAWNPOINT    = 6;
    private static final int FLOOR2_CLIMB_SPAWNPOINT   = 10; // 3 маркера в castle.nbt — любой из них
    private static final int ROOF_SPAWNPOINT           = 11; // 3 маркера в castle.nbt — любой из них

    private static final int TICK_INTERVAL = 20; // раз в секунду
    private static int tickCounter = 0;

    /**
     * Последний реально отправленный снимок счётчиков — без этого сравнения
     * пакет (и, следовательно, полный ре-рендер экрана заданий в браузере)
     * улетал бы каждую секунду даже когда цифры не менялись вообще, что и
     * вызывало заметное "бесконечное обновление" интерфейса.
     */
    private static Map<String, Integer> lastSentCounts = Map.of();

    private CastleSubQuestTask() {}

    // ── Активация по чек-пойнтам ────────────────────────────────────────────────

    /** Вызывается при пересечении Nspawnpoint (см. CastleSpawnPointTask). */
    public static void tryActivate(MinecraftServer server, int spawnpointNumber) {
        String doorId = LABYRINTH_SPAWNPOINT_TO_DOOR.get(spawnpointNumber);
        if (doorId != null) {
            SubQuestState state = SubQuestState.get(server.overworld());
            if (state.activate(DOOR_TO_SUBQUEST.get(doorId))) {
                QuestSync.syncSubQuests(server);
            }
            return;
        }

        if (spawnpointNumber == LABYRINTH_EXIT_SPAWNPOINT) {
            boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("labyrinth_exit");
            boolean subChanged = SubQuestState.get(server.overworld()).activate(SubQuestRegistry.FLOOR1_KILL_GUARDS);
            syncIfChanged(server, mainChanged, subChanged);
            return;
        }

        if (spawnpointNumber == CASTLE_EXIT_SPAWNPOINT) {
            boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("castle_exit_search");
            boolean subChanged = SubQuestState.get(server.overworld()).activate(SubQuestRegistry.KEY_YELLOW_TOWERS);
            syncIfChanged(server, mainChanged, subChanged);
            return;
        }

        if (spawnpointNumber == FLOOR2_CLIMB_SPAWNPOINT) {
            SubQuestState state = SubQuestState.get(server.overworld());
            boolean subChanged = state.complete(SubQuestRegistry.FLOOR2_CLIMB);
            subChanged |= state.activate(SubQuestRegistry.FLOOR2_KILL_GUARDS);
            boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("yellow_towers_enter");
            syncIfChanged(server, mainChanged, subChanged);
            return;
        }

        if (spawnpointNumber == ROOF_SPAWNPOINT) {
            boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("roof_inspect");
            boolean subChanged = SubQuestState.get(server.overworld()).activate(SubQuestRegistry.SHIELD_PLACE_BEACON);
            syncIfChanged(server, mainChanged, subChanged);
        }
    }

    /** Хранитель тайн (blue_tower_boss) убит — вызывается из CastleSpawnManager.onLivingDeath. */
    public static void onBlueTowerGuardianDefeated(MinecraftServer server) {
        SubQuestState state = SubQuestState.get(server.overworld());
        boolean subChanged = state.complete(SubQuestRegistry.BLUE_TOWER_KILL_GUARDIAN);
        boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("blue_tower_inspect");
        syncIfChanged(server, mainChanged, subChanged);
    }

    /** Хелвар (underworld_knight_boss) побеждён — вызывается из CastleBossFightTask.onBossDefeated. */
    public static void onHelvarDefeated(MinecraftServer server) {
        SubQuestState state = SubQuestState.get(server.overworld());
        boolean subChanged = state.complete(SubQuestRegistry.FLOOR2_KILL_BOSS);
        boolean mainChanged = SimulationQuestState.get(server.overworld()).markCompleted("floor2_inspect");
        syncIfChanged(server, mainChanged, subChanged);
    }

    /** Маяк поставлен на алтарь крыши — вызывается из ModEvents.onBeaconPlaced. */
    public static void onBeaconPlaced(MinecraftServer server) {
        SubQuestState state = SubQuestState.get(server.overworld());
        if (state.complete(SubQuestRegistry.SHIELD_PLACE_BEACON)) {
            QuestSync.syncSubQuests(server);
        }
    }

    private static void syncIfChanged(MinecraftServer server, boolean mainChanged, boolean subChanged) {
        if (mainChanged) QuestSync.syncMainQuests(server);
        if (subChanged) QuestSync.syncSubQuests(server);
    }

    // ── Периодическая проверка (раз в секунду) ─────────────────────────────────

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        SubQuestState subQuests = SubQuestState.get(server.overworld());
        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);

        boolean mainChanged = false;
        boolean subChanged = false;
        Map<String, Integer> counts = new HashMap<>();

        // ── Ключи лабиринта: завершение по подбору предмета ──────────────────────
        if (level != null) {
            for (Map.Entry<String, String> e : DOOR_TO_SUBQUEST.entrySet()) {
                String doorId = e.getKey();
                String subId = e.getValue();
                if (!subQuests.isActive(subId)) continue;
                for (ServerPlayer player : level.players()) {
                    if (playerHasKey(player, doorId)) {
                        subChanged |= subQuests.complete(subId);
                        break;
                    }
                }
            }
        }

        if (data.isCastleSpawnSystemInit()) {
            List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());

            // ── Первый этаж: стражи → паладин → floor1_inspect завершён ───────────
            if (subQuests.isActive(SubQuestRegistry.FLOOR1_KILL_GUARDS)) {
                counts.put(SubQuestRegistry.FLOOR1_KILL_GUARDS, CastleSpawnManager.countAlive(defs, data, "floor1"));
                if (CastleSpawnManager.groupCleared(defs, data, "floor1")) {
                    subChanged |= subQuests.complete(SubQuestRegistry.FLOOR1_KILL_GUARDS);
                    subChanged |= subQuests.activate(SubQuestRegistry.FLOOR1_KILL_PALADIN);
                }
            }
            if (subQuests.isActive(SubQuestRegistry.FLOOR1_KILL_PALADIN)
                    && CastleSpawnManager.groupCleared(defs, data, "floor1_boss")) {
                subChanged |= subQuests.complete(SubQuestRegistry.FLOOR1_KILL_PALADIN);
                mainChanged |= SimulationQuestState.get(server.overworld()).markCompleted("floor1_inspect");
            }

            // ── Синяя башня: плита → головоломка → подъём → Хранитель тайн ────────
            if (level != null
                    && !subQuests.isActive(SubQuestRegistry.BLUE_TOWER_PUZZLE)
                    && !subQuests.isActive(SubQuestRegistry.BLUE_TOWER_CLIMB)
                    && !subQuests.isActive(SubQuestRegistry.BLUE_TOWER_KILL_GUARDIAN)
                    && isBlueTowerPlatePressed(level, data)) {
                subChanged |= subQuests.activate(SubQuestRegistry.BLUE_TOWER_PUZZLE);
            }
            if (subQuests.isActive(SubQuestRegistry.BLUE_TOWER_PUZZLE) && data.isBlueTowerPuzzleSolved()) {
                subChanged |= subQuests.complete(SubQuestRegistry.BLUE_TOWER_PUZZLE);
                subChanged |= subQuests.activate(SubQuestRegistry.BLUE_TOWER_CLIMB);
            }
            if (subQuests.isActive(SubQuestRegistry.BLUE_TOWER_CLIMB)
                    && CastleSpawnManager.groupTriggered(defs, data, "blue_tower_boss")) {
                subChanged |= subQuests.complete(SubQuestRegistry.BLUE_TOWER_CLIMB);
                subChanged |= subQuests.activate(SubQuestRegistry.BLUE_TOWER_KILL_GUARDIAN);
            }
            // Завершение BLUE_TOWER_KILL_GUARDIAN — см. onBlueTowerGuardianDefeated
            // (гарантированное событие смерти конкретного моба, не переживает рестарт хуже тика).

            // ── Внешние башни: смерть паладина с ключом жёлтых башен ──────────────
            if (subQuests.isActive(SubQuestRegistry.KEY_YELLOW_TOWERS)
                    && CastleSpawnManager.keyDoorCleared(defs, data, "floor1_yellow_tower")) {
                subChanged |= subQuests.complete(SubQuestRegistry.KEY_YELLOW_TOWERS);
                mainChanged |= SimulationQuestState.get(server.overworld()).markCompleted("outer_towers_inspect");
                subChanged |= subQuests.activate(SubQuestRegistry.FLOOR2_CLIMB);
            }

            // ── Второй этаж: стражники → Хелвар (FLOOR2_CLIMB завершается по 10spawnpoint) ──
            if (subQuests.isActive(SubQuestRegistry.FLOOR2_KILL_GUARDS)) {
                counts.put(SubQuestRegistry.FLOOR2_KILL_GUARDS, CastleSpawnManager.countAlive(defs, data, "floor2"));
                if (CastleSpawnManager.groupCleared(defs, data, "floor2")) {
                    subChanged |= subQuests.complete(SubQuestRegistry.FLOOR2_KILL_GUARDS);
                    subChanged |= subQuests.activate(SubQuestRegistry.FLOOR2_KILL_BOSS);
                }
            }
            // Завершение FLOOR2_KILL_BOSS — см. onHelvarDefeated.

            // ── Крыша: маяк (событийно, onBeaconPlaced) → 3 волны стражей ─────────
            int waveWaiting = data.getRoofSealPhase() == 5 ? data.getRoofWaveWaiting() : 0;
            boolean wave3WasActive = subQuests.isActive(SubQuestRegistry.SHIELD_WAVE3_KILL);
            subChanged |= updateRoofWave(subQuests, defs, data, counts, 1, SubQuestRegistry.SHIELD_WAVE1_KILL, waveWaiting, "roof_wave1");
            subChanged |= updateRoofWave(subQuests, defs, data, counts, 2, SubQuestRegistry.SHIELD_WAVE2_KILL, waveWaiting, "roof_second_wave", "id=roof_second_wave");
            subChanged |= updateRoofWave(subQuests, defs, data, counts, 3, SubQuestRegistry.SHIELD_WAVE3_KILL, waveWaiting, "roof_wave3");
            if (wave3WasActive && !subQuests.isActive(SubQuestRegistry.SHIELD_WAVE3_KILL)) {
                mainChanged |= SimulationQuestState.get(server.overworld()).markCompleted("shield_removal");
            }
            // Подстраховка: если маяк уже поставлен (фаза ушла дальше 1), а подзадание почему-то
            // всё ещё активно — например, событие onBeaconPlaced не дошло из-за рестарта.
            if (subQuests.isActive(SubQuestRegistry.SHIELD_PLACE_BEACON) && data.getRoofSealPhase() > 1) {
                subChanged |= subQuests.complete(SubQuestRegistry.SHIELD_PLACE_BEACON);
            }

            int progress = data.getRoofCapProgressPercent();
            if (progress >= 0) counts.put(SubQuestRegistry.SHIELD_PROGRESS_PERCENT, progress);
        }

        if (mainChanged) QuestSync.syncMainQuests(server);
        if (subChanged) QuestSync.syncSubQuests(server);
        if (!counts.isEmpty() && !counts.equals(lastSentCounts)) {
            lastSentCounts = counts;
            QuestSync.syncSubQuestCounts(server, counts);
        }
    }

    /**
     * Волна крыши: активируется, когда {@code roofWaveWaiting == waveNum}; завершается,
     * когда она была активна и {@code roofWaveWaiting} сменился на другое значение.
     * Волна 2 в castle_roof.nbt раскидана по двум groupId из-за опечатки в структуре
     * ({@code roof_second_wave} и {@code id=roof_second_wave}) — считаем оба.
     *
     * @return true если подзадание было активно и в этом тике завершилось.
     */
    private static boolean updateRoofWave(SubQuestState subQuests, List<CastleSpawnDefinition> defs,
                                           SimulationSavedData data, Map<String, Integer> counts,
                                           int waveNum, String subQuestId, int waveWaiting, String... groupIds) {
        boolean wasActive = subQuests.isActive(subQuestId);
        boolean changed = false;

        if (!wasActive && waveWaiting == waveNum) {
            changed = subQuests.activate(subQuestId);
            wasActive = true;
        }

        if (wasActive) {
            counts.put(subQuestId, CastleSpawnManager.countAlive(defs, data, groupIds));
            if (waveWaiting != waveNum) {
                changed |= subQuests.complete(subQuestId);
            }
        }
        return changed;
    }

    private static boolean playerHasKey(ServerPlayer player, String doorId) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(ModItems.CASTLE_KEY.get())) continue;
            if (stack.hasTag() && doorId.equals(stack.getTag().getString("door_id"))) return true;
        }
        return false;
    }

    /**
     * Плита стоит ровно в центре комнаты головоломки, на 1 блок ниже пьедесталов
     * (проверено по NBT: пьедесталы на Y пьедестального маркера, плита — на Y-1,
     * X/Z плиты — среднее арифметическое X/Z всех 4 пьедесталов). Координаты
     * пьедесталов берём из {@code data.getCastleMarkers()} — они, в отличие от
     * сырых координат из файла .nbt, уже переведены в мировые при расстановке
     * структуры (см. {@link CastlePlacementTask}), поэтому этот путь надёжен
     * независимо от того, где именно был построен замок.
     */
    private static BlockPos blueTowerPlatePos(SimulationSavedData data) {
        List<CastleDataMarker> pedestals = data.getCastleMarkers().stream()
                .filter(m -> m.has("item"))
                .toList();
        if (pedestals.size() != 4) return null;

        int sumX = 0, sumY = 0, sumZ = 0;
        for (CastleDataMarker m : pedestals) {
            sumX += m.pos().getX();
            sumY += m.pos().getY();
            sumZ += m.pos().getZ();
        }
        return new BlockPos(sumX / 4, sumY / 4 - 1, sumZ / 4);
    }

    private static boolean isBlueTowerPlatePressed(ServerLevel level, SimulationSavedData data) {
        BlockPos platePos = blueTowerPlatePos(data);
        if (platePos == null) return false;
        // Не форсируем синхронную подгрузку чанка каждую секунду: если чанк не
        // загружен — на плите заведомо никто не стоит.
        if (!level.isLoaded(platePos)) return false;

        BlockState state = level.getBlockState(platePos);
        return state.hasProperty(BlockStateProperties.POWER)
                && state.getValue(BlockStateProperties.POWER) > 0;
    }
}
