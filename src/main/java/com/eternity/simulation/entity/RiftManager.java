package com.eternity.simulation.entity;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Планировщик разломов с нарастающей прогрессией.
 *
 * <p>Цикл:
 * <ol>
 *   <li>Спавнятся {@code normalNeeded} обычных разломов (по одному из каждого
 *       типа RED/PURPLE/BLUE в случайном порядке).</li>
 *   <li>Затем — один элитный разлом (ELITE).</li>
 *   <li>После закрытия элитного цикл начинается заново.</li>
 * </ol>
 *
 * <p>Прогрессия: {@code normalNeeded = max(0, 3 − eliteCount / 2)}
 * <ul>
 *   <li>0 элитных → 3 обычных</li>
 *   <li>2 элитных → 2 обычных</li>
 *   <li>4 элитных → 1 обычный</li>
 *   <li>6+ элитных → только элитные</li>
 * </ul>
 *
 * <p>Первый разлом появляется ровно через 2 игровых дня после убийства дракона.
 * Координаты и тип каждого разлома определяются за 1 день до спавна и сохраняются
 * в {@link SimulationSavedData} (для будущего детектора).
 *
 * <p>Порядок типов внутри цикла детерминирован по номеру цикла (сид),
 * поэтому восстанавливается после рестарта сервера.
 */
public class RiftManager {

    public static final RiftManager INSTANCE = new RiftManager();
    private static final Logger LOGGER = LogManager.getLogger();

    // ── Задержки (тики) ───────────────────────────────────────────────────────

    /** Задержка первого разлома после убийства дракона: 2 игровых дня. */
    private static final long FIRST_RIFT_DELAY    = 48_000L;
    private static final long INTERVAL_MIN        = 48_000L;  // 2 игровых дня
    private static final long INTERVAL_MAX        = 96_000L;  // 4 игровых дня
    private static final long RETRY_DELAY         =  2_400L;  // 2 мин
    /** За сколько тиков до спавна фиксируем координаты: 1 игровой день. */
    private static final long PREDETERMINE_BEFORE = 24_000L;

    // ── Параметры спавна ──────────────────────────────────────────────────────

    /** Минимум блоков над средней высотой игроков. */
    private static final int ABOVE_PLAYER_MIN = 20;
    /** Максимум блоков над средней высотой игроков. */
    private static final int ABOVE_PLAYER_MAX = 35;
    /** Радиус вокруг центроида (игроки расположены близко, ≤ 200 блоков разброса). */
    private static final int CENTROID_RADIUS = 50;
    /** Радиус вокруг точки спавна одного игрока (разброс > 200 блоков). */
    private static final int SPREAD_RADIUS   = 100;
    /** Порог разброса точек спавна: если все в пределах 200 блоков → центроид. */
    private static final int MAX_SPREAD      = 200;

    // ── In-memory состояние ───────────────────────────────────────────────────

    private UUID activeRiftUUID = null;

    // ── Публичное API ─────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SimulationSavedData data = SimulationSavedData.get(overworld);

        if (!data.isDragonDefeated()) return;
        if (data.isRiftsSealed()) return;  // разломы запечатаны — больше не появляются

        // Фиксируем тик убийства дракона (один раз)
        if (data.getDragonDefeatedAt() == 0) {
            data.setDragonDefeatedAt(overworld.getGameTime());
        }

        // Проверяем, жив ли активный разлом
        if (activeRiftUUID != null) {
            Entity e = overworld.getEntity(activeRiftUUID);
            if (e != null && e.isAlive()) return;
            activeRiftUUID = null;
        }

        // После рестарта activeRiftUUID теряется. Поиск осиротевшего разлома
        // делается один раз в onServerStarted() через adoptOrphanedRift(),
        // а не каждые 100 тиков через getAllEntities().

        long now        = overworld.getGameTime();
        long nextRiftAt = data.getNextRiftScheduledAt();

        // Планируем следующий разлом
        if (nextRiftAt == -1) {
            boolean isFirst = data.getRiftEliteCount() == 0
                           && data.getRiftNormalInCycle() == 0;
            if (isFirst) {
                // Первый разлом: ровно через 2 дня после убийства дракона
                nextRiftAt = data.getDragonDefeatedAt() + FIRST_RIFT_DELAY;
            } else {
                long delay = INTERVAL_MIN
                        + (long)(Math.random() * (INTERVAL_MAX - INTERVAL_MIN));
                nextRiftAt = now + delay;
            }
            data.setNextRiftScheduledAt(nextRiftAt);
            logState(data, "Next rift in " + (nextRiftAt - now) / 20 + "s");
            return;
        }

        // За 1 день до спавна — фиксируем координаты и тип
        if (!data.hasPendingRift() && now >= nextRiftAt - PREDETERMINE_BEFORE) {
            List<ServerPlayer> players = overworld.players();
            if (!players.isEmpty()) {
                determinePendingRift(players, overworld, data);
            }
        }

        if (now < nextRiftAt) return;

        // Пора спавнить
        List<ServerPlayer> players = overworld.players();
        if (players.isEmpty()) {
            data.setNextRiftScheduledAt(now + RETRY_DELAY);
            return;
        }

        // Если игроков не было, когда нужно было определить место — определяем сейчас
        if (!data.hasPendingRift()) {
            determinePendingRift(players, overworld, data);
        }

        boolean spawned = spawnPredetermined(overworld, data);
        if (!spawned) {
            data.clearPendingRift();
            data.setNextRiftScheduledAt(now + RETRY_DELAY);
        }
    }

    /**
     * Вызывается из {@link RiftEntity} при завершении анимации закрытия.
     * Обновляет счётчики прогрессии в SavedData.
     */
    /**
     * Вызывается один раз при старте сервера.
     * Ищет RiftEntity в overworld — если нашли, усыновляем.
     * Это заменяет постоянный поиск через getAllEntities() каждые 100 тиков.
     */
    public void adoptOrphanedRift(ServerLevel overworld) {
        if (activeRiftUUID != null) return;
        for (Entity e : overworld.getAllEntities()) {
            if (e instanceof RiftEntity rift && rift.isAlive()
                    && rift.getState() != RiftEntity.State.CLOSING) {
                activeRiftUUID = rift.getUUID();
                LOGGER.info("[RiftManager] Re-adopted orphaned rift {} ({})",
                        rift.getRiftType(), activeRiftUUID);
                return;
            }
        }
    }

    public void onRiftClosed(UUID riftUUID, RiftEntity.RiftType type) {
        if (!riftUUID.equals(activeRiftUUID)) return;
        activeRiftUUID = null;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        SimulationSavedData data = SimulationSavedData.get(server.overworld());

        if (type == RiftEntity.RiftType.ELITE) {
            data.incrementRiftEliteCount();
            data.resetRiftNormalInCycle();
            LOGGER.info("[RiftManager] Elite closed. Total elites={}. normalNeeded={}",
                    data.getRiftEliteCount(), normalNeeded(data));
        } else {
            data.incrementRiftNormalInCycle();
            LOGGER.info("[RiftManager] Normal ({}) closed. normalInCycle={}/{}",
                    type, data.getRiftNormalInCycle(), normalNeeded(data));
        }
    }

    /** Для тест-команд: регистрирует внешне созданный разлом как активный. */
    public void registerTestRift(UUID entityUUID) {
        activeRiftUUID = entityUUID;
    }

    public void resetState() {
        activeRiftUUID = null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SimulationSavedData data = SimulationSavedData.get(server.overworld());
            data.setNextRiftScheduledAt(-1L);
            data.clearPendingRift();
        }
    }

    public UUID getActiveRiftUUID() { return activeRiftUUID; }

    // ── Логика прогрессии ─────────────────────────────────────────────────────

    /**
     * Сколько обычных разломов нужно до следующего элитного.
     *
     * <p>Прогрессия:
     * <ul>
     *   <li>0–1 элитных → 3 обычных</li>
     *   <li>2-й элитный → 2 обычных</li>
     *   <li>3-й элитный → 1 обычный  ← объединён с 2-м в одну выборку без возвращения</li>
     *   <li>4+ элитных  → только элитные</li>
     * </ul>
     */
    public static int normalNeeded(SimulationSavedData data) {
        return Math.min(3, Math.max(0, 4 - data.getRiftEliteCount()));
    }

    /**
     * Выбирает тип следующего обычного разлома.
     *
     * <p>Фазы:
     * <ul>
     *   <li>Фаза A (0–1 элитных): независимое перемешивание [RED,PURPLE,BLUE] для каждого цикла.</li>
     *   <li>Фаза B (2–3 элитных): единый список без возвращения на 3 позиции (2+1),
     *       гарантирует что все 3 типа встретятся ровно по одному разу.</li>
     * </ul>
     */
    private RiftEntity.RiftType selectNextType(SimulationSavedData data) {
        int needed = normalNeeded(data);
        int done   = data.getRiftNormalInCycle();
        int elites = data.getRiftEliteCount();

        if (done >= needed) return RiftEntity.RiftType.ELITE;

        List<RiftEntity.RiftType> normal = new ArrayList<>(Arrays.asList(
                RiftEntity.RiftType.RED, RiftEntity.RiftType.PURPLE, RiftEntity.RiftType.BLUE));

        if (elites <= 1) {
            // Фаза A: свой сид на каждый цикл, позиция = done
            Collections.shuffle(normal, new Random(elites * 31L + 7919L));
            return normal.get(done);
        } else {
            // Фаза B: один общий сид для элитных 2 и 3 (2+1=3 позиции без повторений)
            // elites=2: позиции 0,1  |  elites=3: позиция 2  (prevDone=2 т.к. normalNeeded(2)=2)
            Collections.shuffle(normal, new Random(7981L));
            int prevDone = (elites == 3) ? 2 : 0; // сколько уже взяли в предыдущем цикле
            return normal.get(done + prevDone);
        }
    }

    // ── Предопределение (за 1 день до спавна) ────────────────────────────────

    /**
     * Определяет тип и координаты следующего разлома, сохраняет в SavedData.
     *
     * <p>Логика выбора точки:
     * <ul>
     *   <li>Ни у кого нет точки спавна → 50 блоков от текущей позиции случайного игрока.</li>
     *   <li>Разброс точек спавна ≤ 200 блоков → 50 блоков от центроида.</li>
     *   <li>Разброс > 200 блоков → 100 блоков от точки спавна случайного игрока.</li>
     * </ul>
     */
    private void determinePendingRift(List<ServerPlayer> players,
                                      ServerLevel level, SimulationSavedData data) {
        RiftEntity.RiftType type = selectNextType(data);

        // Собираем точки привязки (точка спавна или текущая позиция)
        List<double[]> refs    = new ArrayList<>();
        boolean        anySpawn = false;

        for (ServerPlayer p : players) {
            BlockPos spawn = p.getRespawnPosition();
            if (spawn != null && p.getRespawnDimension().equals(Level.OVERWORLD)) {
                refs.add(new double[]{ spawn.getX(), spawn.getZ() });
                anySpawn = true;
            } else {
                refs.add(new double[]{ p.getX(), p.getZ() });
            }
        }

        double cx, cz;
        int    radius;

        if (!anySpawn) {
            // Ни у кого нет точки спавна — случайный игрок, текущая позиция
            ServerPlayer chosen = players.get(level.random.nextInt(players.size()));
            cx     = chosen.getX();
            cz     = chosen.getZ();
            radius = CENTROID_RADIUS;
        } else {
            // Считаем максимальный разброс между точками привязки
            double maxDist = 0;
            for (int i = 0; i < refs.size(); i++) {
                for (int j = i + 1; j < refs.size(); j++) {
                    double dx = refs.get(i)[0] - refs.get(j)[0];
                    double dz = refs.get(i)[1] - refs.get(j)[1];
                    maxDist = Math.max(maxDist, Math.sqrt(dx * dx + dz * dz));
                }
            }

            if (maxDist <= MAX_SPREAD) {
                // Все близко — центроид, радиус 50
                cx     = refs.stream().mapToDouble(r -> r[0]).average().orElse(0);
                cz     = refs.stream().mapToDouble(r -> r[1]).average().orElse(0);
                radius = CENTROID_RADIUS;
            } else {
                // Разброс большой — случайная точка привязки, радиус 100
                double[] chosen = refs.get(level.random.nextInt(refs.size()));
                cx     = chosen[0];
                cz     = chosen[1];
                radius = SPREAD_RADIUS;
            }
        }

        // Равномерное случайное смещение в радиусе (по диску)
        double angle = Math.toRadians(level.random.nextInt(360));
        double r     = radius * Math.sqrt(level.random.nextDouble());
        double x     = cx + Math.cos(angle) * r;
        double z     = cz + Math.sin(angle) * r;
        // 10–40 блоков над средней текущей высотой игроков
        double avgY  = players.stream().mapToDouble(ServerPlayer::getY).average().orElse(64);
        double y     = avgY + ABOVE_PLAYER_MIN
                     + level.random.nextInt(ABOVE_PLAYER_MAX - ABOVE_PLAYER_MIN + 1);

        data.setPendingRift(x, y, z, type.id);
        LOGGER.info("[RiftManager] Predetermined {} rift at ({}, {}, {}), radius={}",
                type, (int) x, (int) y, (int) z, radius);
    }

    // ── Спавн ─────────────────────────────────────────────────────────────────

    private boolean spawnPredetermined(ServerLevel level, SimulationSavedData data) {
        RiftEntity.RiftType type = RiftEntity.RiftType.fromId((byte) data.getPendingRiftType());
        return spawnAt(level, data,
                type, data.getPendingRiftX(), data.getPendingRiftY(), data.getPendingRiftZ(),
                true);
    }

    /** Для тест-команд: спавн рядом с указанным игроком. */
    public boolean spawnRift(ServerPlayer player, ServerLevel level, RiftEntity.RiftType type) {
        double angle = Math.toRadians(level.random.nextInt(360));
        double r     = SPREAD_RADIUS * Math.sqrt(level.random.nextDouble());
        double x     = player.getX() + Math.cos(angle) * r;
        double z     = player.getZ() + Math.sin(angle) * r;
        double y     = player.getY() + ABOVE_PLAYER_MIN
                     + level.random.nextInt(ABOVE_PLAYER_MAX - ABOVE_PLAYER_MIN + 1);
        SimulationSavedData data = SimulationSavedData.get(level);
        return spawnAt(level, data, type, x, y, z, false);
    }

    private boolean spawnAt(ServerLevel level, SimulationSavedData data,
                             RiftEntity.RiftType type,
                             double x, double y, double z, boolean clearPending) {
        float fixedYaw = level.random.nextFloat() * 360f;

        RiftEntity rift = ModEntities.RIFT.get().create(level);
        if (rift == null) return false;

        rift.moveTo(x, y, z, 0f, 0f);
        rift.setFixedYaw(fixedYaw);
        rift.setRiftType(type);

        if (!level.addFreshEntity(rift)) return false;

        activeRiftUUID = rift.getUUID();
        if (clearPending) {
            data.clearPendingRift();
            data.setNextRiftScheduledAt(-1L);
        }

        // Гроза на 1 день
        level.setWeatherParameters(0, 24_000, true, true);

        // Жуткий звук для каждого игрока на сервере
        for (ServerPlayer p : level.players()) {
            p.playNotifySound(SoundEvents.AMBIENT_CAVE.value(),
                    SoundSource.AMBIENT, 5.0f, 0.5f);
        }

        LOGGER.info("[RiftManager] Spawned {} rift at ({}, {}, {})",
                type, (int) x, (int) y, (int) z);
        return true;
    }

    // ── Вспомогательное ──────────────────────────────────────────────────────

    private static void logState(SimulationSavedData data, String msg) {
        LOGGER.debug("[RiftManager] {} | elites={} normalInCycle={}/{} nextType={}",
                msg,
                data.getRiftEliteCount(),
                data.getRiftNormalInCycle(),
                normalNeeded(data),
                data.getRiftNormalInCycle() >= normalNeeded(data)
                        ? "ELITE" : "normal");
    }
}
