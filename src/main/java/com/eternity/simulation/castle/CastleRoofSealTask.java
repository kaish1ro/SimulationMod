package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.network.CameraShakePacket;
import com.eternity.simulation.network.CinematicLookPacket;
import com.eternity.simulation.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Последовательность распечатывания синего силового поля вокруг замка после победы над боссом.
 *
 * <p>Фазы (хранятся в {@link SimulationSavedData#getRoofSealPhase()}):
 * <ul>
 *   <li>0 — неактивно / завершено</li>
 *   <li>1 — PARTICLES: фиолетовые столбы у particle_effect маркеров, ожидание маяка</li>
 *   <li>2 — BEACON_BEAM: луч от маяка, взрыв 14×14 на Y=201, затухание луча (~3 сек)</li>
 *   <li>3 — PINK_CAPS: разрушение 4 розовых крышек (5×5×5) по одной с задержкой 2 сек</li>
 *   <li>4 — RING_EXPAND: расширяющиеся кольца 15×15 → 144×142 на Y=201, по 2.5 сек/кольцо</li>
 *   <li>5 — WAVE_WAIT: пауза разрушения поля пока не убьют всех мобов текущей волны</li>
 *   <li>6 — PERIMETER: снос стен (Y≤200) по 20 блоков/тик без лучей</li>
 *   <li>7 — CLEANUP: снятие барьеров на Y=201, завершение</li>
 * </ul>
 *
 * <p><b>TODO:</b> Замените {@link #PINK_CAP_BLOCK_ID} на реальный ID розового блока из castle_roof.nbt.
 * <b>TODO:</b> Замените {@link #PEDESTAL_BLOCK_ID} на реальный ID блока под маркером beacon_particle_effect.
 */
public final class CastleRoofSealTask {

    // ── Геометрия поля ────────────────────────────────────────────────────────

    /** Абсолютная высота верхнего слоя силового поля. */
    private static final int FIELD_TOP_Y = 203;
    /** Смещение центра поля по X относительно якоря. */
    private static final int FIELD_CENTER_DX = 17;
    /** Смещение центра поля по Z относительно якоря. */
    private static final int FIELD_CENTER_DZ = 4;
    /** Ширина верхнего слоя по X (блоков). */
    private static final int FIELD_WIDTH_X = 144;
    /** Ширина верхнего слоя по Z (блоков). */
    private static final int FIELD_WIDTH_Z = 142;
    /** Точечные поправки периметра: асимметричное расширение за пределы базового width. */
    private static final int FIELD_EXTRA_X_POS = 1; // +1 блок в сторону X+
    private static final int FIELD_EXTRA_X_NEG = 1; // +1 блок в сторону X-
    private static final int FIELD_EXTRA_Z_POS = 1; // +1 блок в сторону Z+
    private static final int FIELD_EXTRA_Z_NEG = 1; // +1 блок в сторону Z-
    /** Полуширина первоначального взрыва маяка (28×28 = ±14 с перекосом). */
    private static final int BEACON_BLAST_HALF_NEG = 14; // -14..+13 = 28 блоков
    private static final int BEACON_BLAST_HALF_POS = 13;

    // ── Тайминги ──────────────────────────────────────────────────────────────

    /** Тик, на котором происходит взрыв (5 сек после установки маяка). */
    private static final int BLAST_DELAY_TICKS   = 100;
    private static final int BEAM_FADE_TICKS     = 140; // 7 сек: взрыв на 5-й, затухание ещё 2 сек
    private static final int CAP_DELAY_TICKS     = 40;  // 2 сек между крышками
    private static final int RING_TICKS          = 50;  // 2.5 сек на кольцо
    private static final int PERIMETER_BATCH     = 50;  // блоков периметра за тик (2.5× от прежних 20)
    private static final int PARTICLE_COLUMN_HALF = 2;  // столб от -2 до +2 = 5 блоков

    // ── Блоки ─────────────────────────────────────────────────────────────────

    /**
     * ID розового блока, из которого сделаны крышки над particle_effect маркерами.
     * TODO: замените на реальный ID блока из castle_roof.nbt.
     */
    public static final String PINK_CAP_BLOCK_ID = "twilightforest:pink_castle_rune_brick";

    /**
     * ID блока, на который игрок должен поставить маяк (под beacon_particle_effect).
     * Используется для исключения из блокировки строительства.
     * TODO: замените на реальный ID.
     */
    public static final String PEDESTAL_BLOCK_ID = "twilightforest:FIXME_pedestal";

    // ── Порог вызовов волн (по номеру кольца) ────────────────────────────────

    // Кольца расширяются от маяка. Взрыв убрал ±14 (28×28), поэтому r стартует с 14.
    // maxRing вычисляется в initFromData (~88). Треть ≈ 39, две трети ≈ 64.
    private static final int RING_START           = 14;
    private static final int WAVE1_RING_THRESHOLD = 39;
    private static final int WAVE2_RING_THRESHOLD = 64;

    // ── Runtime-состояние (сбрасывается при рестарте → восстанавливается из SavedData) ──

    private static ServerLevel activeLevel;
    private static BlockPos anchorPos;
    private static final List<BlockPos> particleMarkers = new ArrayList<>();
    private static BlockPos beaconMarkerPos;

    private static int tickCounter = 0;
    private static int capIndex    = 0;
    private static boolean[] capBeamActive;

    private static int currentRing;
    private static int maxRing;

    private static ArrayDeque<BlockPos> perimeterQueue;

    // Кэш блоков
    private static Block blueForceFieldBlock;
    private static Block amethystLampBlock;
    private static Block rewardChestBlock;

    public static final String AMETHYST_LAMP_BLOCK_ID = "galosphere:amethyst_lamp";
    public static final String REWARD_CHEST_BLOCK_ID  = "quark:prismarine_chest";

    private static final Logger LOGGER = LogManager.getLogger("simulation.CastleRoofSeal");

    private CastleRoofSealTask() {}

    // ── Публичный API ─────────────────────────────────────────────────────────

    public static boolean isActive() {
        return activeLevel != null;
    }

    /** true если XZ-позиция игрока выходит за физические границы силового поля. */
    public static boolean isOutsideField(BlockPos pos) {
        if (anchorPos == null) return false;
        return pos.getX() < fieldMinX() || pos.getX() > fieldMaxX()
            || pos.getZ() < fieldMinZ() || pos.getZ() > fieldMaxZ();
    }

    /**
     * Гарантирует, что якорь для геометрии периметра (см. {@link #isOutsideField})
     * установлен — geometry поля (FIELD_CENTER_DX/DZ/WIDTH) завязана только на
     * anchorPos, а не на конкретные маркеры крыши, поэтому границу можно и нужно
     * держать активной с момента постройки корпуса замка, а не только после
     * {@code /simcastle roof}. Не запускает саму задачу распечатывания — только
     * инициализирует геометрию для ModEvents.tickFieldBoundary. Дёшево, можно
     * звать хоть каждый тик.
     */
    public static void ensureBoundaryAnchor(BlockPos anchor) {
        anchorPos = anchor;
    }

    /** Сбрасывает задачу без изменения мира — для отладки через /simcastle roofstop. */
    public static void stop(SimulationSavedData data) {
        activeLevel    = null;
        perimeterQueue = null;
        particleMarkers.clear();
        beaconMarkerPos  = null;
        capBeamActive    = null;
        rewardChestBlock = null;
        data.setRoofSealActive(false);
        data.setRoofSealPhase(0);
        data.setRoofSealCap(0);
        data.setRoofSealRing(RING_START);
        data.setRoofWaveWaiting(0);
        data.setRoofPhaseAfterWave(0);
        data.setRoofWave1Triggered(false);
        data.setRoofWave2Triggered(false);
        data.setRoofWave3Triggered(false);
        LOGGER.info("[RoofSeal] Задача остановлена командой.");
    }

    /** Позиция ожидаемого маяка (на блок ниже beacon_particle_effect). */
    public static BlockPos getExpectedBeaconPos() {
        return beaconMarkerPos == null ? null : beaconMarkerPos.below();
    }

    /**
     * Запускает задачу после установки castle_roof.nbt.
     * Вызывается из {@link CastlePlacementTask#runRoof}.
     */
    public static void start(ServerLevel level, SimulationSavedData data, BlockPos anchor) {
        initFromData(level, data, anchor);
        if (activeLevel == null) {
            LOGGER.warn("[RoofSeal] start() — activeLevel=null после initFromData. " +
                "particleMarkers={}, beaconMarkerPos={}", particleMarkers.size(), beaconMarkerPos);
            return;
        }

        if (data.getRoofSealPhase() == 0) {
            data.setRoofSealPhase(1); // PARTICLES
            data.setRoofSealActive(true);
            LOGGER.info("[RoofSeal] Задача запущена. particleMarkers={}, beacon={}, anchor={}",
                particleMarkers.size(), beaconMarkerPos, anchor);
        } else {
            LOGGER.info("[RoofSeal] start() вызван повторно, фаза уже {}", data.getRoofSealPhase());
        }
    }

    /**
     * Вызывается из {@code ModEvents.onServerTick} каждый тик.
     * Само восстанавливается после рестарта сервера (если roofSealActive=true в SavedData).
     */
    public static void tick() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isRoofSealActive()) return;

        if (activeLevel == null) {
            // Восстановление после рестарта
            ServerLevel tfLevel = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
            if (tfLevel == null) return;
            initFromData(tfLevel, data, data.getCastleAnchorPos());
            if (activeLevel == null) return;
        }

        tickCounter++;
        boolean particleTick = (tickCounter % 3 == 0); // частицы раз в 3 тика вместо каждого
        int phase = data.getRoofSealPhase();

        switch (phase) {
            case 1 -> { if (particleTick) tickParticles(); }
            case 2 -> tickBeaconBeam(data, particleTick);
            case 3 -> tickPinkCaps(data, particleTick);
            case 4 -> tickRingExpand(data, particleTick);
            case 5 -> tickWaveWait(data, particleTick);
            case 6 -> tickPerimeter(data);
            case 7 -> tickCleanup(data);
        }
    }

    /**
     * Вызывается из {@code ModEvents.onBeaconPlaced} когда игрок ставит маяк на нужную позицию.
     */
    public static void onBeaconPlaced(ServerLevel level, SimulationSavedData data) {
        if (data.getRoofSealPhase() != 1) return;

        LOGGER.info("[RoofSeal] Маяк установлен. beaconMarkerPos={} → взрыв будет на X={} Z={} Y={}",
            beaconMarkerPos,
            beaconMarkerPos == null ? "?" : beaconMarkerPos.getX(),
            beaconMarkerPos == null ? "?" : beaconMarkerPos.getZ(),
            FIELD_TOP_Y);

        // Камера направлена на место взрыва; тряска — только когда произойдёт взрыв
        if (beaconMarkerPos != null) {
            for (ServerPlayer player : level.players()) {
                // 6000 мс = 5 сек до взрыва + 1 сек удержания после
                NetworkHandler.CHANNEL.sendTo(
                    new CinematicLookPacket(beaconMarkerPos.getX(), FIELD_TOP_Y, beaconMarkerPos.getZ(), 6000),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }

        // 0% прогресса HUD — точка отсчёта для проекции "снесено/всего" по крышке.
        data.setRoofCapBlocksTotal(countFieldTopBlocks(level));
        data.addRoofCapBlocksDestroyed(-data.getRoofCapBlocksDestroyed());

        // Взрыв произойдёт через 5 сек (BLAST_DELAY_TICKS) в tickBeaconBeam
        data.setRoofSealPhase(2); // BEACON_BEAM
        tickCounter = 0;
    }

    // ── Инициализация / восстановление ───────────────────────────────────────

    private static void initFromData(ServerLevel level, SimulationSavedData data, BlockPos anchor) {
        particleMarkers.clear();
        beaconMarkerPos = null;

        for (CastleDataMarker marker : data.getCastleMarkers()) {
            // dedup по позиции — защита от повторной установки крыши (дубликаты маркеров)
            if (marker.has("particle_effect") && !particleMarkers.contains(marker.pos())) {
                particleMarkers.add(marker.pos());
            }
            if (marker.has("beacon_particle_effect")) beaconMarkerPos = marker.pos();
        }

        if (particleMarkers.isEmpty() || beaconMarkerPos == null) {
            LOGGER.warn("[RoofSeal] initFromData: particleMarkers={}, beaconMarkerPos={} — задача не запущена. " +
                "Всего маркеров в data: {}", particleMarkers.size(), beaconMarkerPos, data.getCastleMarkers().size());
            return;
        }

        // Диагностика спавн-системы волн крыши
        logRoofWaveSpawns(data);

        activeLevel = level;
        anchorPos   = anchor;

        capIndex      = data.getRoofSealCap();
        currentRing   = data.getRoofSealRing();

        // capBeamActive: крышки до capIndex уже разрушены
        capBeamActive = new boolean[particleMarkers.size()];
        for (int i = 0; i < capIndex && i < capBeamActive.length; i++) {
            capBeamActive[i] = true;
        }

        // Кольца расширяются от МАЯКА. maxRing = max расстояние Чебышёва от маяка до угла поля.
        int bx = beaconMarkerPos.getX();
        int bz = beaconMarkerPos.getZ();
        int dxMax = Math.max(Math.abs(fieldMinX() - bx), Math.abs(fieldMaxX() - bx));
        int dzMax = Math.max(Math.abs(fieldMinZ() - bz), Math.abs(fieldMaxZ() - bz));
        maxRing = Math.max(dxMax, dzMax) + 2;

        tickCounter = 0;
    }

    // ── Геометрия поля: физические границы (центр поля смещён от якоря) ────────
    // ВАЖНО: кольца/лучи расширяются от МАЯКА (beaconMarkerPos), а эти границы —
    // лишь физический экстент поля для отсечки координат.

    private static int fieldMinX() { return anchorPos.getX() + FIELD_CENTER_DX - FIELD_WIDTH_X / 2           - FIELD_EXTRA_X_NEG; }
    private static int fieldMaxX() { return anchorPos.getX() + FIELD_CENTER_DX + (FIELD_WIDTH_X - 1) / 2     + FIELD_EXTRA_X_POS; }
    private static int fieldMinZ() { return anchorPos.getZ() + FIELD_CENTER_DZ - FIELD_WIDTH_Z / 2           - FIELD_EXTRA_Z_NEG; }
    private static int fieldMaxZ() { return anchorPos.getZ() + FIELD_CENTER_DZ + (FIELD_WIDTH_Z - 1) / 2     + FIELD_EXTRA_Z_POS; }

    // ── Фаза 1: фиолетовые столбы ────────────────────────────────────────────

    private static void tickParticles() {
        for (BlockPos marker : particleMarkers) {
            spawnParticleColumn(marker);
        }
    }

    // ── Фаза 2: луч маяка + затухание ────────────────────────────────────────

    private static void tickBeaconBeam(SimulationSavedData data, boolean particleTick) {
        if (particleTick) tickParticles();

        // До взрыва луч полной яркости, после — плавно затухает
        double density;
        if (tickCounter <= BLAST_DELAY_TICKS) {
            density = 1.0;
        } else {
            density = Math.max(0.0, 1.0 - (double)(tickCounter - BLAST_DELAY_TICKS)
                                          / (BEAM_FADE_TICKS - BLAST_DELAY_TICKS));
        }
        if (particleTick && density > 0 && beaconMarkerPos != null) {
            spawnVerticalBeam(beaconMarkerPos.getX(), beaconMarkerPos.getZ(),
                beaconMarkerPos.getY(), FIELD_TOP_Y, density);
        }

        // Ровно через 5 секунд: взрыв + звуки + сильная тряска
        if (tickCounter == BLAST_DELAY_TICKS) {
            destroyInitialBlast(activeLevel, data);
            sendBlastEffectToAll();
        }

        if (tickCounter >= BEAM_FADE_TICKS) {
            // Маяк ломается после взрыва и затухания луча — с эффектом взрыва на нём
            if (beaconMarkerPos != null) {
                BlockPos b = beaconMarkerPos.below();
                activeLevel.setBlock(b, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                activeLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
                activeLevel.sendParticles(ParticleTypes.EXPLOSION,
                    b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 8, 0.6, 0.6, 0.6, 0.0);
                activeLevel.playSound(null, b, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.BLOCKS, 4.0f, 1.0f);
            }
            data.setRoofSealPhase(3); // PINK_CAPS
            data.setRoofSealCap(0);
            capIndex    = 0;
            tickCounter = 0;
        }
    }

    /** Взрывные звуки + сильная тряска для всех игроков в момент разлома поля. */
    private static void sendBlastEffectToAll() {
        // Звук идёт с позиции маяка — именно там образуется дыра
        int bx = beaconMarkerPos != null ? beaconMarkerPos.getX() : anchorPos.getX() + FIELD_CENTER_DX;
        int bz = beaconMarkerPos != null ? beaconMarkerPos.getZ() : anchorPos.getZ() + FIELD_CENTER_DZ;
        LOGGER.info("[RoofSeal] Взрыв! Центр X={} Z={} Y={}", bx, bz, FIELD_TOP_Y);
        // volume=100 → дальность 1600 блоков, достаёт до всех игроков
        activeLevel.playSound(null, bx, FIELD_TOP_Y, bz,
            SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 100.0f, 0.5f);
        activeLevel.playSound(null, bx, FIELD_TOP_Y, bz,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 100.0f, 0.7f);
        for (ServerPlayer player : activeLevel.players()) {
            NetworkHandler.CHANNEL.sendTo(
                new CameraShakePacket(3.5f, 2500),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    // ── Фаза 3: разрушение розовых крышек ────────────────────────────────────

    private static void tickPinkCaps(SimulationSavedData data, boolean particleTick) {
        if (particleTick) {
            tickParticles();
            // Активные лучи от уже разрушённых крышек — вертикально вверх до поля
            for (int i = 0; i < capBeamActive.length; i++) {
                if (capBeamActive[i]) {
                    BlockPos m = particleMarkers.get(i);
                    // Луч выходит из лампы (на 2 блока выше маркера, чем раньше)
                    spawnVerticalBeam(m.getX(), m.getZ(), m.getY() + 2, FIELD_TOP_Y, 1.0);
                }
            }
        }

        // Первый тик нового 2-секундного окна: разрушаем следующую крышку
        if (tickCounter == 1 && capIndex < particleMarkers.size()) {
            BlockPos marker = particleMarkers.get(capIndex);
            destroyCap(marker);
            placeAmethystLamp(marker); // лампа на месте маркера — из неё пойдёт луч
            // Взрыв 2-3 блока над маркером
            double ex = marker.getX() + 0.5, ey = marker.getY() + 2.5, ez = marker.getZ() + 0.5;
            activeLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ex, ey, ez, 1, 0, 0, 0, 0);
            activeLevel.sendParticles(ParticleTypes.EXPLOSION, ex, ey + 0.5, ez, 5, 0.5, 0.5, 0.5, 0);
            activeLevel.playSound(null, marker, SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, 3.0f, 0.85f + activeLevel.getRandom().nextFloat() * 0.15f);
            activeLevel.playSound(null, marker, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 2.0f, 0.5f);
            activeLevel.playSound(null, marker, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0f, 0.4f);
            capBeamActive[capIndex] = true;
            capIndex++;
            data.setRoofSealCap(capIndex);
        }

        if (tickCounter >= CAP_DELAY_TICKS) {
            tickCounter = 0;
            if (capIndex >= particleMarkers.size()) {
                // Все крышки разрушены — переходим к кольцам (с r=29, взрыв уже убрал центр)
                currentRing = RING_START;
                data.setRoofSealRing(currentRing);
                data.setRoofSealPhase(4); // RING_EXPAND
            }
        }
    }

    // ── Фаза 4: расширяющиеся кольца ─────────────────────────────────────────

    private static void tickRingExpand(SimulationSavedData data, boolean particleTick) {
        if (particleTick) {
            spawnRingBeams(); // столбы-частицы здесь не нужны — поле уже разрушается
        }

        if (tickCounter < RING_TICKS) return;
        tickCounter = 0;

        // Разрушаем текущее кольцо
        destroyRing(currentRing, data);
        currentRing++;
        data.setRoofSealRing(currentRing);

        // Проверяем триггеры волн
        if (!data.isRoofWave1Triggered() && currentRing > WAVE1_RING_THRESHOLD) {
            data.setRoofWave1Triggered(true);
            triggerRoofWave(data, "roof_wave1");
            enterWaveWait(data, 1, 4);
            return;
        }
        if (!data.isRoofWave2Triggered() && currentRing > WAVE2_RING_THRESHOLD) {
            data.setRoofWave2Triggered(true);
            triggerRoofWave(data, "roof_second_wave");
            triggerRoofWave(data, "id=roof_second_wave");
            enterWaveWait(data, 2, 4);
            return;
        }
        if (currentRing > maxRing) {
            // Верхний слой полностью разрушен — запускаем волну 3, сундук появится
            // позже, в startPerimeter() — когда реально начнётся снос стен по бокам.
            if (!data.isRoofWave3Triggered()) {
                data.setRoofWave3Triggered(true);
                triggerRoofWave(data, "roof_wave3");
                enterWaveWait(data, 3, 6); // после волны 3 → периметр
                return;
            }
            startPerimeter(data);
        }
    }

    // ── Фаза 5: ожидание волны ────────────────────────────────────────────────

    private static void tickWaveWait(SimulationSavedData data, boolean particleTick) {
        // Пока волна жива — частицы аметистовых ламп (и любые другие) полностью убраны,
        // чтобы не отвлекали от боя; возобновятся сами, когда фаза вернётся к кольцам.

        int waveNum = data.getRoofWaveWaiting();
        if (!isWaveCleared(data, waveNum)) return;

        int nextPhase = data.getRoofPhaseAfterWave();
        data.setRoofWaveWaiting(0);
        data.setRoofPhaseAfterWave(0);
        tickCounter = 0;

        if (nextPhase == 6) {
            startPerimeter(data);
        } else {
            data.setRoofSealPhase(4); // обратно к кольцам
        }
    }

    // ── Фаза 6: периметр (стены) ──────────────────────────────────────────────

    private static void tickPerimeter(SimulationSavedData data) {
        if (perimeterQueue == null) {
            perimeterQueue = buildPerimeterQueue();
        }

        // 20 блоков за тик = «довольно быстро»
        for (int i = 0; i < PERIMETER_BATCH && !perimeterQueue.isEmpty(); i++) {
            BlockPos pos = perimeterQueue.poll();
            Block ff = getBlueForceField();
            if (ff != null && activeLevel.getBlockState(pos).getBlock() == ff) {
                activeLevel.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }

        if (perimeterQueue.isEmpty()) {
            data.setRoofSealPhase(7); // CLEANUP
            tickCounter = 0;
        }
    }

    // ── Фаза 7: снятие барьеров ───────────────────────────────────────────────

    private static void tickCleanup(SimulationSavedData data) {
        clearTopBarriers(); // на случай если ещё остались — обычно уже пусто (см. startPerimeter)

        data.setRoofSealPhase(0);
        data.setRoofSealActive(false);
        // Поле окончательно снято — все ограничения перехвата/перестройки замка
        // (жемчуги, граница, ломание/установка блоков) снимаются абсолютно.
        data.setCastleLockdownActive(false);
        activeLevel  = null;
        perimeterQueue = null;
    }

    /** Снимает барьеры (временная замена крышки на Y=FIELD_TOP_Y) во всех границах поля. */
    private static void clearTopBarriers() {
        for (int x = fieldMinX(); x <= fieldMaxX(); x++) {
            for (int z = fieldMinZ(); z <= fieldMaxZ(); z++) {
                BlockPos pos = new BlockPos(x, FIELD_TOP_Y, z);
                if (activeLevel.getBlockState(pos).getBlock() == Blocks.BARRIER) {
                    activeLevel.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    private static void spawnParticleColumn(BlockPos marker) {
        // Плотный столб высотой ~5 блоков — частица на каждые 0.5 блока, без разброса
        for (int i = 0; i <= 10; i++) {
            double y = marker.getY() - 1 + i * 0.5;
            activeLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                marker.getX() + 0.5, y + 0.5, marker.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnVerticalBeam(int bx, int bz, int fromY, int toY, double density) {
        if (density <= 0 || activeLevel == null) return;
        // Плотный сплошной столб: частица почти на каждый блок (density=1.0 → ровная линия)
        for (int y = fromY; y <= toY; y++) {
            if (density >= 1.0 || activeLevel.getRandom().nextDouble() < density) {
                activeLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    bx + 0.5, y, bz + 0.5,
                    1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Лучи от активных маркеров к серединам сторон текущего кольца.
     * Кольцо расширяется от МАЯКА. Каждый маркер определяет свою сторону по направлению
     * от маяка: если |dx| ≥ |dz| — Восток/Запад, иначе — Север/Юг. Цель — середина этой
     * стороны на расстоянии r (кламп по физическим границам поля).
     */
    private static void spawnRingBeams() {
        if (particleMarkers.isEmpty() || beaconMarkerPos == null) return;

        int cx = beaconMarkerPos.getX();
        int cz = beaconMarkerPos.getZ();
        int r  = currentRing;

        for (int i = 0; i < particleMarkers.size(); i++) {
            if (capBeamActive == null || i >= capBeamActive.length || !capBeamActive[i]) continue;
            BlockPos m = particleMarkers.get(i);

            // Направление маркера от маяка (центр расширения)
            int dx = m.getX() - cx;
            int dz = m.getZ() - cz;

            int targetX, targetZ;
            if (Math.abs(dx) >= Math.abs(dz)) {
                // Восток или Запад: цель — середина боковой стороны кольца
                int raw = cx + (dx >= 0 ? r : -r);
                targetX = Math.max(fieldMinX(), Math.min(fieldMaxX(), raw));
                targetZ = cz;
            } else {
                // Север или Юг
                targetX = cx;
                int raw = cz + (dz >= 0 ? r : -r);
                targetZ = Math.max(fieldMinZ(), Math.min(fieldMaxZ(), raw));
            }

            spawnDiagonalBeam(m.getX(), m.getY() + 1, m.getZ(), targetX, FIELD_TOP_Y, targetZ);
        }
    }

    /** Рисует плотный сплошной луч от (x0,y0,z0) до (x1,y1,z1) — частица на каждые ~0.4 блока. */
    private static void spawnDiagonalBeam(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (activeLevel == null) return;
        double ddx = x1 - x0, ddy = y1 - y0, ddz = z1 - z0;
        double length = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (length < 1) return;
        int steps = Math.max(1, (int) (length / 0.4)); // плотно: ~2.5 частицы на блок
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            activeLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                x0 + ddx * t + 0.5, y0 + ddy * t, z0 + ddz * t + 0.5,
                1, 0.0, 0.0, 0.0, 0.0); // без разброса — ровная линия
        }
    }

    /** Взрыв маяка: 28×28 на FIELD_TOP_Y, центр = позиция beacon_particle_effect по XZ. */
    private static void destroyInitialBlast(ServerLevel level, SimulationSavedData data) {
        Block ff = getBlueForceField();
        if (ff == null || beaconMarkerPos == null) {
            LOGGER.warn("[RoofSeal] destroyInitialBlast пропущен: ff={} beaconMarkerPos={}", ff != null, beaconMarkerPos);
            return;
        }

        int cx = beaconMarkerPos.getX();
        int cz = beaconMarkerPos.getZ();
        LOGGER.info("[RoofSeal] destroyInitialBlast: центр X={} Z={} Y={}, радиус -{}/+{}", cx, cz, FIELD_TOP_Y, BEACON_BLAST_HALF_NEG, BEACON_BLAST_HALF_POS);

        int destroyed = 0;
        for (int dx = -BEACON_BLAST_HALF_NEG; dx <= BEACON_BLAST_HALF_POS; dx++) {
            for (int dz = -BEACON_BLAST_HALF_NEG; dz <= BEACON_BLAST_HALF_POS; dz++) {
                BlockPos pos = new BlockPos(cx + dx, FIELD_TOP_Y, cz + dz);
                if (level.getBlockState(pos).getBlock() == ff) {
                    level.setBlock(pos, Blocks.BARRIER.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    destroyed++;
                }
            }
        }
        data.addRoofCapBlocksDestroyed(destroyed);
    }

    /** Считает блоки {@code blue_force_field} на Y=FIELD_TOP_Y в границах поля — точка отсчёта прогресса. */
    private static int countFieldTopBlocks(ServerLevel level) {
        Block ff = getBlueForceField();
        if (ff == null) return 0;
        int count = 0;
        for (int x = fieldMinX(); x <= fieldMaxX(); x++) {
            for (int z = fieldMinZ(); z <= fieldMaxZ(); z++) {
                if (level.getBlockState(new BlockPos(x, FIELD_TOP_Y, z)).getBlock() == ff) count++;
            }
        }
        return count;
    }

    /** Разрушает 5×5×5 блоков над маркером (Y+1 до Y+5). */
    private static void destroyCap(BlockPos marker) {
        Block capBlock = null;
        var capRes = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(PINK_CAP_BLOCK_ID));
        if (capRes != null && capRes != Blocks.AIR) capBlock = capRes;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    BlockPos pos = marker.offset(dx, dy, dz);
                    BlockState state = activeLevel.getBlockState(pos);
                    // Если капблок известен — сносим только его; иначе сносим всё не-воздух
                    if (capBlock == null ? !state.isAir() : state.getBlock() == capBlock) {
                        activeLevel.setBlock(pos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
    }

    /** Разрушает одно кольцо на Чебышёвском радиусе r от МАЯКА на Y=FIELD_TOP_Y. */
    private static void destroyRing(int r, SimulationSavedData data) {
        Block ff = getBlueForceField();
        if (ff == null || beaconMarkerPos == null) return;

        int cx = beaconMarkerPos.getX();
        int cz = beaconMarkerPos.getZ();
        int minX = fieldMinX(), maxX = fieldMaxX(), minZ = fieldMinZ(), maxZ = fieldMaxZ();

        // Верхняя и нижняя строки кольца
        for (int dx = -r; dx <= r; dx++) {
            int x = cx + dx;
            if (x < minX || x > maxX) continue;

            int z1 = cz - r;
            if (z1 >= minZ && z1 <= maxZ) replaceWithBarrier(new BlockPos(x, FIELD_TOP_Y, z1), ff, data);

            int z2 = cz + r;
            if (z2 != z1 && z2 >= minZ && z2 <= maxZ) replaceWithBarrier(new BlockPos(x, FIELD_TOP_Y, z2), ff, data);
        }

        // Левая и правая колонки (без угловых — уже покрыты выше)
        for (int dz = -(r - 1); dz <= (r - 1); dz++) {
            int z = cz + dz;
            if (z < minZ || z > maxZ) continue;

            int x1 = cx - r;
            if (x1 >= minX && x1 <= maxX) replaceWithBarrier(new BlockPos(x1, FIELD_TOP_Y, z), ff, data);

            int x2 = cx + r;
            if (x2 != x1 && x2 >= minX && x2 <= maxX) replaceWithBarrier(new BlockPos(x2, FIELD_TOP_Y, z), ff, data);
        }
    }

    private static void replaceWithBarrier(BlockPos pos, Block ff, SimulationSavedData data) {
        if (activeLevel.getBlockState(pos).getBlock() == ff) {
            activeLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            data.addRoofCapBlocksDestroyed(1);
        }
    }

    private static void triggerRoofWave(SimulationSavedData data, String groupId) {
        if (activeLevel == null) return;
        int n = CastleSpawnManager.triggerGroup(activeLevel, data, groupId);
        if (n == 0) {
            LOGGER.warn("[RoofSeal] triggerRoofWave('{}') — НИ ОДНОЙ точки спавна не найдено! " +
                "Проверь id= маркеров волны в castle_roof.nbt", groupId);
        } else {
            LOGGER.info("[RoofSeal] triggerRoofWave('{}') — активировано точек спавна: {}", groupId, n);
        }
    }

    /** Диагностика: выводит все mob-маркеры волн крыши с их состоянием. */
    private static void logRoofWaveSpawns(SimulationSavedData data) {
        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        int roofCount = 0;
        for (CastleSpawnDefinition d : defs) {
            String g = d.groupId();
            if (g == null) continue;
            if (g.startsWith("roof_wave") || g.startsWith("id=roof_") || "roof_second_wave".equals(g)) {
                roofCount++;
                LOGGER.info("[RoofSeal] волна-спавн: id='{}' index={} mob={} count={} triggered={} alive={}",
                    g, d.index(), d.mobId(), d.count(),
                    data.isSpawnTriggered(d.index()), data.getSpawnAlive(d.index()));
            }
        }
        LOGGER.info("[RoofSeal] particle_effect маркеров: {}, волновых точек спавна: {}",
            particleMarkers.size(), roofCount);
        if (roofCount == 0) {
            LOGGER.warn("[RoofSeal] НЕТ ни одной волновой точки спавна — поле сломается без ожидания мобов!");
        }
    }

    private static void enterWaveWait(SimulationSavedData data, int waveNum, int phaseAfter) {
        data.setRoofWaveWaiting(waveNum);
        data.setRoofPhaseAfterWave(phaseAfter);
        data.setRoofSealPhase(5); // WAVE_WAIT
        tickCounter = 0;
    }

    /** Проверяет, все ли мобы волны номер waveNum убиты. */
    private static boolean isWaveCleared(SimulationSavedData data, int waveNum) {
        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        String groupId = switch (waveNum) {
            case 1 -> "roof_wave1";
            case 3 -> "roof_wave3";
            default -> null; // волна 2: несколько groupId
        };

        if (waveNum == 2) {
            // Обе варианта groupId (с двойным id= и без)
            return isGroupDone(defs, data, "roof_second_wave")
                && isGroupDone(defs, data, "id=roof_second_wave");
        }

        if (groupId == null) return true;
        return isGroupDone(defs, data, groupId);
    }

    /** true если нет ни одной незачищенной точки спавна данной группы. */
    private static boolean isGroupDone(List<CastleSpawnDefinition> defs, SimulationSavedData data, String groupId) {
        for (CastleSpawnDefinition d : defs) {
            if (!groupId.equals(d.groupId())) continue;
            if (!data.isSpawnTriggered(d.index()) || data.getSpawnAlive(d.index()) > 0) return false;
        }
        return true;
    }

    private static void startPerimeter(SimulationSavedData data) {
        spawnRewardChest(); // именно тут поле реально начинает уходить по бокам
        clearTopBarriers(); // крышки (барьеры вместо снесённого поля) убираем тоже сейчас, не ждём CLEANUP
        perimeterQueue = buildPerimeterQueue();
        data.setRoofSealPhase(6); // PERIMETER
        tickCounter = 0;
    }

    /** Собирает очередь всех блоков силового поля вниз до Y=50 (сверху вниз, только blue_force_field). */
    private static ArrayDeque<BlockPos> buildPerimeterQueue() {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Block ff = getBlueForceField();
        if (ff == null || anchorPos == null) return queue;

        int minX = fieldMinX(), maxX = fieldMaxX();
        int minZ = fieldMinZ(), maxZ = fieldMaxZ();
        final int Y_MIN = 50;

        // Сносим слоями сверху вниз: Y во внешнем цикле, только периметр
        for (int y = FIELD_TOP_Y - 1; y >= Y_MIN; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onEdge = x <= minX + 3 || x >= maxX - 3
                                  || z <= minZ + 3 || z >= maxZ - 3;
                    if (!onEdge) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (activeLevel.getBlockState(pos).getBlock() == ff) {
                        queue.add(pos);
                    }
                }
            }
        }
        return queue;
    }

    /** Ставит призмариновый сундук с наградой на месте маяка при разрушении верхнего слоя. */
    private static void spawnRewardChest() {
        if (beaconMarkerPos == null || activeLevel == null) return;
        BlockPos chestPos = beaconMarkerPos.below();

        Block chestBlock = getRewardChestBlock();
        activeLevel.setBlock(chestPos, chestBlock.defaultBlockState(), Block.UPDATE_CLIENTS);

        if (activeLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            net.minecraft.world.item.Item heartItem =
                ForgeRegistries.ITEMS.getValue(new ResourceLocation("cyclic", "heart"));
            for (int slot = 0; slot < 27; slot++) {
                if (slot == 13 && heartItem != null && heartItem != Items.AIR) {
                    chest.setItem(slot, new ItemStack(heartItem));
                } else if (slot != 13) {
                    chest.setItem(slot, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
                }
            }
        }

        // Эффект появления: золотые/белые частицы + фанфары
        double cx = chestPos.getX() + 0.5, cy = chestPos.getY() + 0.5, cz = chestPos.getZ() + 0.5;
        activeLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, cy, cz, 40, 0.5, 0.5, 0.5, 0.3);
        activeLevel.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 20, 0.3, 0.5, 0.3, 0.1);
        activeLevel.playSound(null, chestPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
            SoundSource.MASTER, 3.0f, 1.0f);
        LOGGER.info("[RoofSeal] Наградной сундук поставлен на {}", chestPos);
    }

    private static Block getRewardChestBlock() {
        if (rewardChestBlock == null) {
            rewardChestBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(REWARD_CHEST_BLOCK_ID));
            if (rewardChestBlock == null || rewardChestBlock == Blocks.AIR) {
                LOGGER.warn("[RoofSeal] Блок {} не найден, используется обычный сундук", REWARD_CHEST_BLOCK_ID);
                rewardChestBlock = Blocks.CHEST;
            }
        }
        return rewardChestBlock;
    }

    private static Block getBlueForceField() {
        if (blueForceFieldBlock == null) {
            blueForceFieldBlock = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("twilightforest", "blue_force_field"));
        }
        return blueForceFieldBlock;
    }

    /** Ставит galosphere:amethyst_lamp на месте particle_effect маркера (откуда пойдёт луч). */
    private static void placeAmethystLamp(BlockPos marker) {
        if (amethystLampBlock == null) {
            amethystLampBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(AMETHYST_LAMP_BLOCK_ID));
        }
        if (amethystLampBlock == null || amethystLampBlock == Blocks.AIR) {
            LOGGER.warn("[RoofSeal] Блок {} не найден — лампа не поставлена", AMETHYST_LAMP_BLOCK_ID);
            return;
        }
        activeLevel.setBlock(marker, amethystLampBlock.defaultBlockState(),
            Block.UPDATE_CLIENTS);
    }
}
