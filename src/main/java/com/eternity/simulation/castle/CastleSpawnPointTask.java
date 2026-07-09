package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Точки возрождения замка: DATA-маркеры с ключом вида {@code Nspawnpoint}
 * (например {@code 1spawnpoint}, {@code 2spawnpoint}).
 *
 * <ul>
 *   <li>если у маркера есть {@code radius=N} — точка спавна устанавливается, когда
 *       игрок входит в радиус {@code N} блоков от маркера;</li>
 *   <li>если радиуса нет — игрок должен встать ровно на блок маркера.</li>
 * </ul>
 *
 * <p>Точка возрождения ставится на {@code marker.pos().below()}. Каждый маркер
 * активируется не более одного раза на игрока (отслеживается в persistentData
 * игрока по координатам маркера), независимо от других маркеров — в том числе
 * с тем же числовым префиксом {@code N}.
 */
public final class CastleSpawnPointTask {

    private static final Pattern SPAWNPOINT_KEY = Pattern.compile("\\d+spawnpoint");

    /** Тег persistentData игрока: CompoundTag с булевыми флагами "x_y_z" -> true. */
    private static final String NBT_ACTIVATED = "simulation_activated_spawnpoints";

    /**
     * Поколение постройки замка, при котором игрок активировал свои чек-пойнты.
     * При перестройке замка (/simcastle build) координаты маркеров не меняются,
     * поэтому без сброса по поколению старые флаги "уже активирован" блокировали
     * бы все чек-пойнты после ребилда.
     */
    private static final String NBT_GENERATION = "simulation_spawnpoints_generation";

    /**
     * Номера чек-пойнтов, у которых несколько физических маркеров с одинаковым
     * N должны сработать только один раз на весь мир (не на игрока) — см.
     * {@link SimulationSavedData#markSpawnpointGloballyTriggered}. Плюс их
     * радиус активации увеличен на {@link #GLOBAL_ONCE_RADIUS_BONUS} блоков —
     * маркеры стоят в местах, где точное попадание неудобно физически.
     */
    private static final Set<Integer> GLOBAL_ONCE_SPAWNPOINTS = Set.of(10, 11);
    private static final int GLOBAL_ONCE_RADIUS_BONUS = 3;

    /**
     * Допуск (в блоках, по расстоянию) для чек-пойнтов БЕЗ явного {@code radius} в NBT —
     * лабиринт (2-5spawnpoint) полагался на дословное совпадение позиции, и быстрый игрок
     * (спринт/прыжки по коридору) иногда проскакивал ровно этот блок между двумя
     * замерами и вообще не срабатывал. Небольшой допуск делает их такими же надёжными,
     * как чек-пойнты с radius.
     */
    private static final double DEFAULT_EXACT_TOLERANCE = 1.5;

    private static final int TICK_INTERVAL = 5; // 4 раза в секунду — реже пропускает быстрое движение
    private static int tickCounter = 0;

    private CastleSpawnPointTask() {}

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (level == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isCastleSpawnSystemInit()) return;

        List<CastleDataMarker> markers = data.getCastleMarkers();
        if (markers.isEmpty() || level.players().isEmpty()) return;

        // Замок перестроили — сбрасываем игрокам флаги активированных чек-пойнтов
        int generation = data.getCastleBuildGeneration();
        for (ServerPlayer player : level.players()) {
            CompoundTag pd = player.getPersistentData();
            if (pd.getInt(NBT_GENERATION) != generation) {
                pd.putInt(NBT_GENERATION, generation);
                pd.remove(NBT_ACTIVATED);
            }
        }

        for (CastleDataMarker marker : markers) {
            String spawnpointKey = marker.keys().stream()
                    .filter(k -> SPAWNPOINT_KEY.matcher(k).matches())
                    .findFirst().orElse(null);
            if (spawnpointKey == null) continue;

            int spawnpointNumber = parseSpawnpointNumber(spawnpointKey);
            boolean globalOnce = GLOBAL_ONCE_SPAWNPOINTS.contains(spawnpointNumber);

            // 10/11spawnpoint — по 3 физических маркера на номер; как только один
            // сработал у кого угодно, остальные с тем же номером больше не активируются.
            if (globalOnce && data.isSpawnpointGloballyTriggered(spawnpointNumber)) continue;

            int radius = marker.has("radius") ? marker.getInt("radius", -1) : -1;
            if (globalOnce && radius > 0) radius += GLOBAL_ONCE_RADIUS_BONUS;
            BlockPos respawnPos = marker.pos().below();
            String markerKey = marker.pos().getX() + "_" + marker.pos().getY() + "_" + marker.pos().getZ();

            for (ServerPlayer player : level.players()) {
                CompoundTag activated = player.getPersistentData().getCompound(NBT_ACTIVATED);
                if (activated.getBoolean(markerKey)) continue;

                // Маркер стоит на блок выше пола (как и все DATA-маркеры в структуре) —
                // без радиуса игрок физически стоит на marker.pos().below(), а не на
                // самом marker.pos(); раньше сравнение шло с marker.pos() напрямую,
                // и попасть в него можно было только пролетая мимо в прыжке.
                boolean reached = radius > 0
                        ? player.blockPosition().distSqr(marker.pos()) <= (double) radius * radius
                        : player.blockPosition().distSqr(respawnPos) <= DEFAULT_EXACT_TOLERANCE * DEFAULT_EXACT_TOLERANCE;

                if (!reached) continue;

                player.setRespawnPosition(level.dimension(), respawnPos, 0.0f, true, true);

                activated.putBoolean(markerKey, true);
                player.getPersistentData().put(NBT_ACTIVATED, activated);

                if (globalOnce) {
                    data.markSpawnpointGloballyTriggered(spawnpointNumber);
                }

                // Квесты/подзадания Симуляции (см. CastleSubQuestTask) — активируются
                // тем же прохождением чек-пойнта, если для его номера есть запись.
                if (spawnpointNumber >= 0) {
                    CastleSubQuestTask.tryActivate(server, spawnpointNumber);
                }

                if (globalOnce) break; // номер уже потреблён — не пускаем других игроков в этот же тик
            }
        }
    }

    private static int parseSpawnpointNumber(String spawnpointKey) {
        try {
            return Integer.parseInt(spawnpointKey.substring(0, spawnpointKey.length() - "spawnpoint".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
