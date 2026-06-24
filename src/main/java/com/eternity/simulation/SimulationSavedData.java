package com.eternity.simulation;

import com.eternity.simulation.castle.CastleDataMarker;
import com.eternity.simulation.castle.CastleSpawnDefinition;
import com.eternity.simulation.ollama.OllamaMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.Arrays;

public class SimulationSavedData extends SavedData {

    private static final String NAME = "simulation_progress";

    // ── Поля ─────────────────────────────────────────────────────────────────

    private boolean dragonDefeated = false;

    /** Флаг: Питер уже заспавнен в мире. */
    private boolean observerSpawned = false;

    /**
     * Флаг: администратор снял WIP-блокировку с остальных измерений.
     * Командой /simportal unlock.
     */
    private boolean portalWipUnlocked = false;

    public boolean isPortalWipUnlocked()      { return portalWipUnlocked; }
    public void    setPortalWipUnlocked(boolean v) { portalWipUnlocked = v; setDirty(); }

    /**
     * Глобальный флаг: хотя бы один игрок впервые вошёл в Нижний мир.
     * Устанавливается один раз и не сбрасывается.
     */
    private boolean hasEnteredNether = false;

    /**
     * История диалогов с Наблюдателем — отдельная на каждого игрока.
     * Ключ — UUID игрока, значение — список сообщений (включая system prompt).
     */
    private final Map<UUID, List<OllamaMessage>> observerHistory = new HashMap<>();

    // ── Rift progression ─────────────────────────────────────────────────────

    /** Сколько всего элитных разломов было закрыто. */
    private int riftEliteCount = 0;

    /**
     * Сколько обычных разломов появилось в текущем цикле (с момента последнего элитного).
     * Когда достигает {@code max(0, 3 - eliteCount/2)} — следующий разлом элитный.
     */
    private int riftNormalInCycle = 0;

    public int  getRiftEliteCount()              { return riftEliteCount; }
    public int  getRiftNormalInCycle()           { return riftNormalInCycle; }

    public void incrementRiftEliteCount()        { riftEliteCount++;      setDirty(); }
    public void incrementRiftNormalInCycle()     { riftNormalInCycle++;   setDirty(); }
    public void resetRiftNormalInCycle()         { riftNormalInCycle = 0; setDirty(); }

    // ── Rift timing ───────────────────────────────────────────────────────────

    /** Игровой тик, когда был убит дракон. 0 = ещё не зафиксировано. */
    private long dragonDefeatedAt    = 0L;

    /**
     * Игровой тик, когда появится следующий разлом.
     * -1 = не запланировано. Персистится для детектора в будущем.
     */
    private long nextRiftScheduledAt = -1L;

    /** Предопределённые координаты следующего разлома (за 1 день до спавна). */
    private double pendingRiftX    = 0;
    private double pendingRiftY    = 0;
    private double pendingRiftZ    = 0;
    /** id типа разлома; -1 = ещё не определён. */
    private int    pendingRiftType = -1;

    public long    getDragonDefeatedAt()    { return dragonDefeatedAt; }
    public void    setDragonDefeatedAt(long tick)  { dragonDefeatedAt = tick; setDirty(); }

    public long    getNextRiftScheduledAt() { return nextRiftScheduledAt; }
    public void    setNextRiftScheduledAt(long tick) { nextRiftScheduledAt = tick; setDirty(); }

    public boolean hasPendingRift()         { return pendingRiftType != -1; }
    public double  getPendingRiftX()        { return pendingRiftX; }
    public double  getPendingRiftY()        { return pendingRiftY; }
    public double  getPendingRiftZ()        { return pendingRiftZ; }
    public int     getPendingRiftType()     { return pendingRiftType; }

    public void setPendingRift(double x, double y, double z, int type) {
        pendingRiftX    = x;
        pendingRiftY    = y;
        pendingRiftZ    = z;
        pendingRiftType = type;
        setDirty();
    }

    public void clearPendingRift() { pendingRiftType = -1; setDirty(); }

    // ── Boss scaling: отслеживание активных игроков ──────────────────────────

    /**
     * UUID → System.currentTimeMillis() последнего визита игрока.
     * Используется для вычисления «реального» числа игроков при скейле боссов.
     */
    private final Map<UUID, Long> playerLastSeen = new HashMap<>();

    /** 3 реальных дня в миллисекундах. */
    private static final long PLAYER_ACTIVE_WINDOW_MS = 3L * 24 * 60 * 60 * 1000;

    /**
     * Записывает/обновляет метку времени последнего появления игрока.
     * Вызывается при входе и периодически пока игрок онлайн.
     */
    public void recordPlayerSeen(UUID playerUUID) {
        playerLastSeen.put(playerUUID, System.currentTimeMillis());
        setDirty();
    }

    /**
     * Возвращает количество «активных» игроков — тех, кто заходил
     * в течение последних 3 реальных дней. Минимум 1.
     */
    public int getScalingPlayerCount() {
        long now    = System.currentTimeMillis();
        long cutoff = now - PLAYER_ACTIVE_WINDOW_MS;
        int count   = (int) playerLastSeen.values().stream()
                .filter(t -> t >= cutoff)
                .count();
        return Math.max(1, count);
    }

    // ── Wanderer ─────────────────────────────────────────────────────────────

    /** Счётчик встреч со Скитальцем — отдельный на каждого игрока. */
    private final Map<UUID, Integer> wandererEncounterCount = new HashMap<>();

    /** История диалогов со Скитальцем — отдельная на каждого игрока. */
    private final Map<UUID, List<OllamaMessage>> wandererHistory = new HashMap<>();

    // ── Dragon ───────────────────────────────────────────────────────────────

    /**
     * Флаг: сцена выхода из Края уже была сыграна (Скиталец уже встретил
     * первого вышедшего игрока). Устанавливается один раз и не сбрасывается.
     */
    private boolean exitSceneTriggered = false;

    public boolean isExitSceneTriggered()           { return exitSceneTriggered; }
    public void    setExitSceneTriggered(boolean v) { exitSceneTriggered = v; setDirty(); }

    /**
     * Флаг: запечатывающая структура была активирована — разломы больше не появляются.
     * Устанавливается один раз и не сбрасывается.
     */
    private boolean riftsSealed = false;

    public boolean isRiftsSealed()           { return riftsSealed; }
    public void    setRiftsSealed(boolean v) { riftsSealed = v; setDirty(); }

    public boolean isDragonDefeated() { return dragonDefeated; }

    public void setDragonDefeated(boolean value) {
        this.dragonDefeated = value;
        setDirty();
    }

    // ── Nether flag ──────────────────────────────────────────────────────────

    public boolean hasEnteredNether() { return hasEnteredNether; }

    public void setHasEnteredNether(boolean value) {
        this.hasEnteredNether = value;
        setDirty();
    }

    // ── Observer spawn ───────────────────────────────────────────────────────

    public boolean isObserverSpawned() { return observerSpawned; }

    public void setObserverSpawned(boolean value) {
        this.observerSpawned = value;
        setDirty();
    }

    // ── Observer dialog history ───────────────────────────────────────────────

    /** Возвращает копию истории диалога для заданного игрока (пустой список если нет). */
    public List<OllamaMessage> getObserverHistory(UUID playerUUID) {
        return new ArrayList<>(observerHistory.getOrDefault(playerUUID, List.of()));
    }

    /** Сохраняет историю диалога для игрока. */
    public void setObserverHistory(UUID playerUUID, List<OllamaMessage> history) {
        observerHistory.put(playerUUID, new ArrayList<>(history));
        setDirty();
    }

    // ── Wanderer getters/setters ──────────────────────────────────────────────

    public int getWandererEncounterCount(UUID playerUUID) {
        return wandererEncounterCount.getOrDefault(playerUUID, 0);
    }

    public void incrementWandererEncounterCount(UUID playerUUID) {
        wandererEncounterCount.merge(playerUUID, 1, Integer::sum);
        setDirty();
    }

    public List<OllamaMessage> getWandererHistory(UUID playerUUID) {
        return new ArrayList<>(wandererHistory.getOrDefault(playerUUID, List.of()));
    }

    public void setWandererHistory(UUID playerUUID, List<OllamaMessage> history) {
        wandererHistory.put(playerUUID, new ArrayList<>(history));
        setDirty();
    }

    // ── Якорь финального замка TF ────────────────────────────────────────────

    /**
     * Позиция WIP-стенда "Final Castle WIP." (см. ModEvents.onEntityJoinLevel) —
     * единственная точка центрального корпуса замка, фиксированная по форме
     * и ориентации (всегда SOUTH) независимо от рандома боковых башен/лабиринтов.
     * Захватывается один раз при первой загрузке соответствующего чанка.
     */
    private boolean castleAnchorSet = false;
    private BlockPos castleAnchorPos = BlockPos.ZERO;

    public boolean hasCastleAnchor() { return castleAnchorSet; }
    public BlockPos getCastleAnchorPos() { return castleAnchorPos; }

    /** Сохраняет якорь, если он ещё не был сохранён (первый раз — побеждает). */
    public void setCastleAnchorPos(BlockPos pos) {
        if (castleAnchorSet) return;
        this.castleAnchorPos = pos.immutable();
        this.castleAnchorSet = true;
        setDirty();
    }

    /** Флаг: страж замка побеждён (финальный бой завершён). */
    private boolean castleGuardianDefeated = false;

    public boolean isCastleGuardianDefeated()      { return castleGuardianDefeated; }
    public void    setCastleGuardianDefeated(boolean v) { castleGuardianDefeated = v; setDirty(); }

    /** Флаг: головоломка с пьедесталами синей башни решена (статуи/лестницы открыты, blue_tower заспавнен). */
    private boolean blueTowerPuzzleSolved = false;

    public boolean isBlueTowerPuzzleSolved()      { return blueTowerPuzzleSolved; }
    public void    setBlueTowerPuzzleSolved(boolean v) { blueTowerPuzzleSolved = v; setDirty(); }

    // ── Битва с главным боссом 2-го этажа (CastleBossFightTask) ──────────────

    /**
     * Стадия босс-файта:
     * 0 — ждём гибели группы floor2;
     * 1 — вступление (молнии, спавн босса);
     * 2 — ждём падения HP до 25% (волна 1);
     * 3 — волна 1 активна, босс неуязвим;
     * 4 — HP восстановлено, ждём падения до 50% (волна 2);
     * 5 — волна 2 активна, босс неуязвим;
     * 6 — финал, босс уязвим до смерти;
     * 7 — босс побеждён, лут выдан.
     */
    private int bossFightStage = 0;

    /** UUID заспавненного главного босса (block_factorys_bosses:underworld_knight), или {@code null}. */
    private UUID bossEntityUuid = null;

    public int     getBossFightStage()       { return bossFightStage; }
    public void    setBossFightStage(int v)  { bossFightStage = v; setDirty(); }

    public UUID    getBossEntityUuid()       { return bossEntityUuid; }
    public void    setBossEntityUuid(UUID v) { bossEntityUuid = v; setDirty(); }

    // ── Блокировка строительства (замковая арена) ────────────────────────────

    /**
     * UUID игроков, которым временно запрещено ломать/ставить блоки —
     * пока они находятся в арене замкового стража.
     */
    private final Set<UUID> buildLockedPlayers = new HashSet<>();

    public boolean isBuildLocked(UUID playerUUID) {
        return buildLockedPlayers.contains(playerUUID);
    }

    /** Устанавливает или снимает блокировку строительства для игрока. */
    public void setBuildLocked(UUID playerUUID, boolean locked) {
        boolean changed = locked ? buildLockedPlayers.add(playerUUID)
                                  : buildLockedPlayers.remove(playerUUID);
        if (changed) setDirty();
    }

    // ── Запечатывание силового поля крыши (CastleRoofSealTask) ───────────────

    private boolean roofSealActive = false;
    /** 0=нет, 1=частицы, 2=луч маяка, 3=крышки, 4=кольца, 5=ожидание волны, 6=периметр, 7=очистка */
    private int roofSealPhase = 0;
    private int roofSealCap = 0;
    private int roofSealRing = 7;
    private int roofWaveWaiting = 0;
    private int roofPhaseAfterWave = 0;
    private boolean roofWave1Triggered = false;
    private boolean roofWave2Triggered = false;
    private boolean roofWave3Triggered = false;

    public boolean isRoofSealActive()           { return roofSealActive; }
    public void    setRoofSealActive(boolean v) { roofSealActive = v; setDirty(); }
    public int  getRoofSealPhase()            { return roofSealPhase; }
    public void setRoofSealPhase(int v)       { roofSealPhase = v; setDirty(); }
    public int  getRoofSealCap()              { return roofSealCap; }
    public void setRoofSealCap(int v)         { roofSealCap = v; setDirty(); }
    public int  getRoofSealRing()             { return roofSealRing; }
    public void setRoofSealRing(int v)        { roofSealRing = v; setDirty(); }
    public int  getRoofWaveWaiting()          { return roofWaveWaiting; }
    public void setRoofWaveWaiting(int v)     { roofWaveWaiting = v; setDirty(); }
    public int  getRoofPhaseAfterWave()       { return roofPhaseAfterWave; }
    public void setRoofPhaseAfterWave(int v)  { roofPhaseAfterWave = v; setDirty(); }
    public boolean isRoofWave1Triggered()     { return roofWave1Triggered; }
    public void setRoofWave1Triggered(boolean v) { roofWave1Triggered = v; setDirty(); }
    public boolean isRoofWave2Triggered()     { return roofWave2Triggered; }
    public void setRoofWave2Triggered(boolean v) { roofWave2Triggered = v; setDirty(); }
    public boolean isRoofWave3Triggered()     { return roofWave3Triggered; }
    public void setRoofWave3Triggered(boolean v) { roofWave3Triggered = v; setDirty(); }

    /**
     * Расширяет массивы спавн-системы для новых точек спавна (добавляются маркеры крыши).
     * Сохраняет всё существующее состояние; новые записи инициализируются счётчиком из def.
     */
    public void extendCastleSpawnSystem(List<CastleSpawnDefinition> allDefs) {
        if (!castleSpawnSystemInit) return;
        int newSize = allDefs.size();
        if (newSize <= castleSpawnTriggered.length) return;
        int oldSize = castleSpawnTriggered.length;
        castleSpawnTriggered = Arrays.copyOf(castleSpawnTriggered, newSize);
        castleSpawnAlive     = Arrays.copyOf(castleSpawnAlive,     newSize);
        for (CastleSpawnDefinition def : allDefs) {
            if (def.index() >= oldSize) {
                castleSpawnAlive[def.index()] = def.count();
            }
        }
        setDirty();
    }

    // ── Маркеры замка (DATA-блоки из castle.nbt/labyrinth.nbt) ───────────────

    /**
     * DATA-маркеры (мобы/лут/двери), собранные при установке castle.nbt и labyrinth.nbt
     * через {@code /simcastle build}. Заполняется один раз.
     */
    private final List<CastleDataMarker> castleMarkers = new ArrayList<>();

    public List<CastleDataMarker> getCastleMarkers() {
        return Collections.unmodifiableList(castleMarkers);
    }

    public void setCastleMarkers(List<CastleDataMarker> markers) {
        castleMarkers.clear();
        castleMarkers.addAll(markers);
        setDirty();
    }

    // ── Спавн-система замка (мобы по триггерам, ключи с keyid) ────────────────

    /** Флаг: спавн-система инициализирована (после успешного {@code /simcastle build}). */
    private boolean castleSpawnSystemInit = false;

    /** Триггер уже сработал для точки спавна с данным index'ом. */
    private boolean[] castleSpawnTriggered = new boolean[0];

    /** Сколько мобов из точки спавна с данным index'ом ещё живы. */
    private int[] castleSpawnAlive = new int[0];

    public boolean isCastleSpawnSystemInit() { return castleSpawnSystemInit; }

    /** Инициализирует состояние спавн-системы (один раз, после постройки замка). */
    public void initCastleSpawnSystem(List<CastleSpawnDefinition> defs) {
        castleSpawnTriggered = new boolean[defs.size()];
        castleSpawnAlive = new int[defs.size()];
        for (CastleSpawnDefinition def : defs) {
            castleSpawnAlive[def.index()] = def.count();
        }
        castleSpawnSystemInit = true;
        setDirty();
    }

    public boolean isSpawnTriggered(int index) {
        return index >= 0 && index < castleSpawnTriggered.length && castleSpawnTriggered[index];
    }

    public void setSpawnTriggered(int index) {
        if (index >= 0 && index < castleSpawnTriggered.length) {
            castleSpawnTriggered[index] = true;
            setDirty();
        }
    }

    public int getSpawnAlive(int index) {
        return (index >= 0 && index < castleSpawnAlive.length) ? castleSpawnAlive[index] : 0;
    }

    /** Принудительно выставляет счётчик живых мобов (сверка с реальными сущностями). */
    public void setSpawnAlive(int index, int value) {
        if (index >= 0 && index < castleSpawnAlive.length && castleSpawnAlive[index] != value) {
            castleSpawnAlive[index] = Math.max(0, value);
            setDirty();
        }
    }

    /** Уменьшает счётчик живых мобов точки спавна, возвращает новое значение (минимум 0). */
    public int decrementSpawnAlive(int index) {
        if (index < 0 || index >= castleSpawnAlive.length) return 0;
        castleSpawnAlive[index] = Math.max(0, castleSpawnAlive[index] - 1);
        setDirty();
        return castleSpawnAlive[index];
    }

    // ── Сериализация ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("dragonDefeated", dragonDefeated);
        tag.putBoolean("observerSpawned", observerSpawned);
        tag.putBoolean("hasEnteredNether", hasEnteredNether);
        tag.putBoolean("portalWipUnlocked", portalWipUnlocked);
        tag.putBoolean("exitSceneTriggered", exitSceneTriggered);
        tag.putBoolean("riftsSealed",        riftsSealed);
        tag.putInt("riftEliteCount",       riftEliteCount);
        tag.putInt("riftNormalInCycle",    riftNormalInCycle);
        tag.putLong("dragonDefeatedAt",    dragonDefeatedAt);
        tag.putLong("nextRiftScheduledAt", nextRiftScheduledAt);
        if (pendingRiftType != -1) {
            tag.putDouble("pendingRiftX",    pendingRiftX);
            tag.putDouble("pendingRiftY",    pendingRiftY);
            tag.putDouble("pendingRiftZ",    pendingRiftZ);
            tag.putByte("pendingRiftType",   (byte) pendingRiftType);
        }

        tag.put("observerHistories",  serializeHistories(observerHistory));
        tag.put("wandererHistories",  serializeHistories(wandererHistory));
        tag.put("wandererEncounters", serializeCounters(wandererEncounterCount));
        tag.put("playerLastSeen",     serializeLastSeen(playerLastSeen));
        tag.put("buildLockedPlayers", serializeUuidSet(buildLockedPlayers));

        tag.putBoolean("castleAnchorSet", castleAnchorSet);
        if (castleAnchorSet) {
            tag.putInt("castleAnchorX", castleAnchorPos.getX());
            tag.putInt("castleAnchorY", castleAnchorPos.getY());
            tag.putInt("castleAnchorZ", castleAnchorPos.getZ());
        }
        tag.putBoolean("castleGuardianDefeated", castleGuardianDefeated);
        tag.putBoolean("blueTowerPuzzleSolved", blueTowerPuzzleSolved);
        tag.put("castleMarkers", serializeCastleMarkers(castleMarkers));

        tag.putInt("bossFightStage", bossFightStage);
        if (bossEntityUuid != null) {
            tag.putUUID("bossEntityUuid", bossEntityUuid);
        }

        tag.putBoolean("castleSpawnSystemInit", castleSpawnSystemInit);
        if (castleSpawnSystemInit) {
            tag.putByteArray("castleSpawnTriggered", boolArrayToByteArray(castleSpawnTriggered));
            tag.putIntArray("castleSpawnAlive", castleSpawnAlive);
        }

        tag.putBoolean("roofSealActive",      roofSealActive);
        tag.putInt("roofSealPhase",           roofSealPhase);
        tag.putInt("roofSealCap",             roofSealCap);
        tag.putInt("roofSealRing",            roofSealRing);
        tag.putInt("roofWaveWaiting",         roofWaveWaiting);
        tag.putInt("roofPhaseAfterWave",      roofPhaseAfterWave);
        tag.putBoolean("roofWave1Triggered",  roofWave1Triggered);
        tag.putBoolean("roofWave2Triggered",  roofWave2Triggered);
        tag.putBoolean("roofWave3Triggered",  roofWave3Triggered);

        return tag;
    }

    public static SimulationSavedData load(CompoundTag tag) {
        SimulationSavedData data = new SimulationSavedData();
        data.dragonDefeated      = tag.getBoolean("dragonDefeated");
        data.observerSpawned     = tag.getBoolean("observerSpawned");
        data.hasEnteredNether    = tag.getBoolean("hasEnteredNether");
        data.portalWipUnlocked   = tag.getBoolean("portalWipUnlocked");
        data.exitSceneTriggered  = tag.getBoolean("exitSceneTriggered");
        data.riftsSealed         = tag.getBoolean("riftsSealed");
        data.riftEliteCount       = tag.getInt("riftEliteCount");
        data.riftNormalInCycle    = tag.getInt("riftNormalInCycle");
        data.dragonDefeatedAt     = tag.getLong("dragonDefeatedAt");
        // getLong возвращает 0 для отсутствующего ключа — используем contains для -1
        data.nextRiftScheduledAt  = tag.contains("nextRiftScheduledAt")
                ? tag.getLong("nextRiftScheduledAt") : -1L;
        if (tag.contains("pendingRiftType")) {
            data.pendingRiftX    = tag.getDouble("pendingRiftX");
            data.pendingRiftY    = tag.getDouble("pendingRiftY");
            data.pendingRiftZ    = tag.getDouble("pendingRiftZ");
            data.pendingRiftType = tag.getByte("pendingRiftType");
        }

        deserializeHistories(tag.getCompound("observerHistories"), data.observerHistory);
        deserializeHistories(tag.getCompound("wandererHistories"),  data.wandererHistory);
        deserializeCounters(tag.getCompound("wandererEncounters"),  data.wandererEncounterCount);
        deserializeLastSeen(tag.getCompound("playerLastSeen"),      data.playerLastSeen);
        deserializeUuidSet(tag.getList("buildLockedPlayers", Tag.TAG_STRING), data.buildLockedPlayers);

        data.castleAnchorSet = tag.getBoolean("castleAnchorSet");
        if (data.castleAnchorSet) {
            data.castleAnchorPos = new BlockPos(
                    tag.getInt("castleAnchorX"),
                    tag.getInt("castleAnchorY"),
                    tag.getInt("castleAnchorZ"));
        }
        data.castleGuardianDefeated = tag.getBoolean("castleGuardianDefeated");
        data.blueTowerPuzzleSolved = tag.getBoolean("blueTowerPuzzleSolved");
        deserializeCastleMarkers(tag.getList("castleMarkers", Tag.TAG_COMPOUND), data.castleMarkers);

        data.bossFightStage = tag.getInt("bossFightStage");
        if (tag.hasUUID("bossEntityUuid")) {
            data.bossEntityUuid = tag.getUUID("bossEntityUuid");
        }

        data.castleSpawnSystemInit = tag.getBoolean("castleSpawnSystemInit");
        if (data.castleSpawnSystemInit) {
            data.castleSpawnTriggered = byteArrayToBoolArray(tag.getByteArray("castleSpawnTriggered"));
            data.castleSpawnAlive = tag.getIntArray("castleSpawnAlive");
        }

        data.roofSealActive      = tag.getBoolean("roofSealActive");
        data.roofSealPhase       = tag.getInt("roofSealPhase");
        data.roofSealCap         = tag.getInt("roofSealCap");
        data.roofSealRing        = tag.contains("roofSealRing") ? tag.getInt("roofSealRing") : 7;
        data.roofWaveWaiting     = tag.getInt("roofWaveWaiting");
        data.roofPhaseAfterWave  = tag.getInt("roofPhaseAfterWave");
        data.roofWave1Triggered  = tag.getBoolean("roofWave1Triggered");
        data.roofWave2Triggered  = tag.getBoolean("roofWave2Triggered");
        data.roofWave3Triggered  = tag.getBoolean("roofWave3Triggered");

        return data;
    }

    // ── Вспомогательные методы сериализации ──────────────────────────────────

    private static CompoundTag serializeHistories(Map<UUID, List<OllamaMessage>> map) {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<UUID, List<OllamaMessage>> entry : map.entrySet()) {
            ListTag list = new ListTag();
            for (OllamaMessage msg : entry.getValue()) {
                CompoundTag m = new CompoundTag();
                m.putString("role",    msg.role());
                m.putString("content", msg.content());
                list.add(m);
            }
            root.put(entry.getKey().toString(), list);
        }
        return root;
    }

    private static void deserializeHistories(CompoundTag root, Map<UUID, List<OllamaMessage>> target) {
        for (String key : root.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag list = root.getList(key, Tag.TAG_COMPOUND);
                List<OllamaMessage> history = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag m = list.getCompound(i);
                    history.add(new OllamaMessage(m.getString("role"), m.getString("content")));
                }
                target.put(uuid, history);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static CompoundTag serializeCounters(Map<UUID, Integer> map) {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            root.putInt(entry.getKey().toString(), entry.getValue());
        }
        return root;
    }

    private static void deserializeCounters(CompoundTag root, Map<UUID, Integer> target) {
        for (String key : root.getAllKeys()) {
            try {
                target.put(UUID.fromString(key), root.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static CompoundTag serializeLastSeen(Map<UUID, Long> map) {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : map.entrySet()) {
            root.putLong(entry.getKey().toString(), entry.getValue());
        }
        return root;
    }

    private static void deserializeLastSeen(CompoundTag root, Map<UUID, Long> target) {
        for (String key : root.getAllKeys()) {
            try {
                target.put(UUID.fromString(key), root.getLong(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static ListTag serializeUuidSet(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID id : set) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static void deserializeUuidSet(ListTag list, Set<UUID> target) {
        for (int i = 0; i < list.size(); i++) {
            try {
                target.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static ListTag serializeCastleMarkers(List<CastleDataMarker> markers) {
        ListTag list = new ListTag();
        for (CastleDataMarker marker : markers) {
            CompoundTag m = new CompoundTag();
            m.putInt("x", marker.pos().getX());
            m.putInt("y", marker.pos().getY());
            m.putInt("z", marker.pos().getZ());
            m.putString("meta", marker.rawMetadata());
            list.add(m);
        }
        return list;
    }

    private static void deserializeCastleMarkers(ListTag list, List<CastleDataMarker> target) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            BlockPos pos = new BlockPos(m.getInt("x"), m.getInt("y"), m.getInt("z"));
            target.add(new CastleDataMarker(pos, m.getString("meta")));
        }
    }

    private static byte[] boolArrayToByteArray(boolean[] arr) {
        byte[] out = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i] ? (byte) 1 : (byte) 0;
        }
        return out;
    }

    private static boolean[] byteArrayToBoolArray(byte[] arr) {
        boolean[] out = new boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i] != 0;
        }
        return out;
    }

    // ── Точка доступа ─────────────────────────────────────────────────────────

    public static SimulationSavedData get(ServerLevel level) {
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(
                SimulationSavedData::load,
                SimulationSavedData::new,
                NAME
            );
    }
}
