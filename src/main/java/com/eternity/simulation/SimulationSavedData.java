package com.eternity.simulation;

import com.eternity.simulation.ollama.OllamaMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

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
