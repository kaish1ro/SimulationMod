package com.eternity.simulation.entity;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.ollama.OllamaClient;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Координирует все встречи со Скитальцем.
 *
 * <p>Жизненный цикл:
 * <ol>
 *   <li>Игрок впервые входит в Нижний мир → устанавливает глобальный флаг
 *       {@code hasEnteredNether} и планирует первую встречу.</li>
 *   <li>Менеджер периодически проверяет условия (не в бою, в Верхнем мире).</li>
 *   <li>При подходящих условиях — спавнит единственного глобального Скитальца.</li>
 *   <li>Скиталец самостоятельно подходит к игроку (состояние APPROACHING).</li>
 *   <li>После деспауна — планирует следующую встречу.</li>
 * </ol>
 *
 * <p>Важно: в любой момент не более одного активного Скитальца на сервере.
 */
public class EncounterManager {

    public static final EncounterManager INSTANCE = new EncounterManager();
    private static final Logger LOGGER = LogManager.getLogger();

    private final OllamaClient ollamaClient = new OllamaClient();

    // ── Задержки (в тиках) ────────────────────────────────────────────────────

    /** Задержка первой встречи после выхода из Нижнего мира. */
    private static final int FIRST_MIN  = 1200;  // 1 мин
    private static final int FIRST_MAX  = 3600;  // 3 мин

    /** Задержка между встречами после деспауна предыдущего Скитальца. */
    private static final int REPEAT_MIN = 6000;  // 5 мин
    private static final int REPEAT_MAX = 18000; // 15 мин

    /** Задержка повтора если условия не подошли. */
    private static final int RETRY_DELAY = 2400; // 2 мин

    /** Переключить в true для дебаг-сообщений в чат операторам. */
    private static final boolean DEBUG_CHAT = false;

    // ── In-memory состояние (не сохраняется, восстанавливается при рестарте) ─

    /** UUID сущности активного Скитальца (null — нет активной встречи). */
    private UUID activeWandererEntityUUID = null;

    /**
     * Игровое время, когда должна произойти встреча.
     * -1 = не запланировано.
     */
    private long nextEncounterAt = -1;

    /** Целевой игрок для ближайшей встречи. */
    private UUID scheduledTargetUUID = null;

    /**
     * Сколько раз подряд не удалось найти позицию под землёй.
     * После {@value #UNDERGROUND_CLOSE_THRESHOLD} ретраев переключаемся
     * на спавн вплотную за спиной игрока.
     */
    private int undergroundRetryCount = 0;
    private static final int UNDERGROUND_CLOSE_THRESHOLD = 4; // ~8 мин при RETRY_DELAY=2400

    // ── Дебаг в чат (только для операторов) ──────────────────────────────────

    /** Отправляет дебаг-сообщение всем операторам. Включить: DEBUG_CHAT = true. */
    private static void debugChat(MinecraftServer server, String message) {
        if (!DEBUG_CHAT) return;
        Component msg = Component.literal("§8[Enc] §7" + message);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(msg);
            }
        }
    }

    // ── Триггер: первый выход из Нижнего мира ────────────────────────────────

    /**
     * Вызывается из {@code ModEvents} при переходе игрока Ад → Верхний мир.
     * Если флаг уже стоит — ничего не делаем.
     */
    public void onPlayerExitedNether(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        SimulationSavedData data = SimulationSavedData.get(sl);
        if (data.hasEnteredNether()) return;

        data.setHasEnteredNether(true);
        LOGGER.info("[EncounterManager] First Nether exit by {}. Scheduling first encounter.",
                player.getName().getString());

        // Планируем первую встречу с тем, кто вышел из Ада
        if (nextEncounterAt == -1) {
            scheduledTargetUUID = player.getUUID();
            scheduleEncounter(sl.getGameTime(), FIRST_MIN, FIRST_MAX);
            debugChat(player.getServer(), "Первый выход из Ада (" + player.getName().getString()
                    + "). Встреча через " + (nextEncounterAt - sl.getGameTime()) / 20 + "с");
        }
    }

    // ── Тестовые хелперы (используются WandererCommand) ──────────────────────

    /** Регистрирует Скитальца, созданного командой, как активную встречу. */
    public void registerTestWanderer(UUID entityUUID) {
        activeWandererEntityUUID = entityUUID;
    }

    /** Сбрасывает всё in-memory состояние (не трогает SavedData). */
    public void resetState() {
        activeWandererEntityUUID = null;
        nextEncounterAt = -1;
        scheduledTargetUUID = null;
        undergroundRetryCount = 0;
    }

    /** Возвращает читаемую строку статуса для команды /simwander status. */
    public String getStatusString(long now) {
        StringBuilder sb = new StringBuilder();
        sb.append("§7  activeWanderer: §f")
          .append(activeWandererEntityUUID != null ? activeWandererEntityUUID : "нет");
        sb.append("\n§7  nextEncounterAt: §f");
        if (nextEncounterAt == -1) {
            sb.append("не запланировано");
        } else {
            long diff = nextEncounterAt - now;
            if (diff <= 0) sb.append("прямо сейчас");
            else sb.append("через ").append(diff / 20).append(" сек");
        }
        sb.append("\n§7  scheduledTarget: §f")
          .append(scheduledTargetUUID != null ? scheduledTargetUUID : "нет");
        return sb.toString();
    }

    // ── Счётчик встреч ────────────────────────────────────────────────────────

    /** Вызывается из WandererEntity когда NPC поприветствовал игрока. */
    public void onEncounterGreeted(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        SimulationSavedData.get(sl).incrementWandererEncounterCount(player.getUUID());
    }

    // ── Деспаун Скитальца ─────────────────────────────────────────────────────

    /** Вызывается из {@code WandererEntity.tickLeaving()} перед remove(). */
    public void onWandererDespawned(UUID targetPlayerUUID) {
        activeWandererEntityUUID = null;
        // Выгружаем модель — она больше не нужна до следующей встречи
        ollamaClient.unloadModel();
        LOGGER.debug("[EncounterManager] Wanderer despawned (target={}), unloading model", targetPlayerUUID);
    }

    // ── Тик (каждые 100 тиков = 5 сек) ──────────────────────────────────────

    public void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        SimulationSavedData data = SimulationSavedData.get(overworld);

        // ── 1. Проверяем жив ли активный Скиталец ───────────────────────────
        if (activeWandererEntityUUID != null) {
            Entity entity = overworld.getEntity(activeWandererEntityUUID);
            if (entity != null && entity.isAlive()) {
                return; // Встреча в процессе — не мешаем
            }
            // Сущность исчезла не через нормальный деспаун (напр. /kill) — чистим
            debugChat(server, "Скиталец исчез (убит или выгружен). Планирую новую встречу.");
            activeWandererEntityUUID = null;
        }

        // ── 2. Флаг не установлен — встреч ещё не должно быть ───────────────
        if (!data.hasEnteredNether()) return;

        // ── 3. Нет запланированной встречи — ждём (встречи только по триггеру Ада) ──
        if (nextEncounterAt == -1) return;

        // ── 4. Таймер ещё не истёк ───────────────────────────────────────────
        if (now < nextEncounterAt) return;

        // ── 5. Ищем цель ─────────────────────────────────────────────────────
        ServerPlayer target = resolveTarget(server);
        if (target == null) {
            debugChat(server, "Нет доступных игроков. Retry через " + RETRY_DELAY / 20 + "с");
            nextEncounterAt = now + RETRY_DELAY;
            return;
        }

        // ── 6. Проверяем условия ─────────────────────────────────────────────
        if (!canEncounter(target)) {
            String reason = target.level().dimension() != Level.OVERWORLD ? "не в Верхнем мире"
                         : SpawnHelper.isInCombat(target)  ? "в бою"
                         : SpawnHelper.isSwimming(target)  ? "плывёт"
                         : "??";
            debugChat(server, "Условия не выполнены (" + target.getName().getString()
                    + " — " + reason + "). Retry через " + RETRY_DELAY / 20 + "с");
            nextEncounterAt = now + RETRY_DELAY;
            return;
        }

        // ── 7. Спавним ───────────────────────────────────────────────────────
        boolean spawned = spawnWandererFor(target, overworld, server);
        if (spawned) {
            nextEncounterAt = -1;
            scheduledTargetUUID = null;
        } else {
            debugChat(server, "Не нашёл позицию спавна для " + target.getName().getString()
                    + ". Retry через " + RETRY_DELAY / 20 + "с");
            nextEncounterAt = now + RETRY_DELAY;
        }
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private void scheduleEncounter(long now, int minDelay, int maxDelay) {
        long delay = minDelay + (long)(Math.random() * (maxDelay - minDelay));
        nextEncounterAt = now + delay;
    }

    /**
     * Пытается вернуть запланированного игрока; если тот недоступен —
     * выбирает случайного из подходящих.
     * Возвращает null если в оверворлде меньше 2 игроков.
     */
    private ServerPlayer resolveTarget(MinecraftServer server) {
        // Сначала проверяем — достаточно ли игроков онлайн
        if (server.getPlayerList().getPlayerCount() < 2) return null;

        if (scheduledTargetUUID != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(scheduledTargetUUID);
            if (p != null && p.level().dimension() == Level.OVERWORLD) return p;
        }
        return pickTargetPlayer(server);
    }

    /**
     * Случайный игрок в Верхнем мире.
     * Встреча запускается только если на сервере онлайн ≥ 2 игроков
     * (второй может быть в любом измерении — в Аду, в другом измерении и т.д.).
     */
    private ServerPlayer pickTargetPlayer(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() < 2) return null;
        List<ServerPlayer> candidates = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.level().dimension() == Level.OVERWORLD)
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.get((int)(Math.random() * candidates.size()));
    }

    /**
     * Финальные условия перед спавном:
     * <ul>
     *   <li>Игрок в Верхнем мире</li>
     *   <li>Не в бою (не получал урон от моба последние 5 сек)</li>
     * </ul>
     */
    private boolean canEncounter(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD) return false;
        if (SpawnHelper.isInCombat(player))                return false;
        if (SpawnHelper.isSwimming(player))                return false;
        return true;
    }

    /**
     * Ищет позицию спавна и создаёт Скитальца.
     *
     * <p>Стратегия:
     * <ul>
     *   <li>Поверхность — 30-60 блоков вперёд по взгляду</li>
     *   <li>Под землёй — 15-25 блоков вперёд (пещера)</li>
     *   <li>Под землёй слишком долго — 2-4 блока строго за спиной</li>
     * </ul>
     *
     * @return true если Скиталец успешно создан и добавлен в мир.
     */
    private boolean spawnWandererFor(ServerPlayer player, ServerLevel overworld, MinecraftServer server) {
        BlockPos spawnPos;
        String mode;

        if (!SpawnHelper.isUnderground(player)) {
            undergroundRetryCount = 0;
            spawnPos = SpawnHelper.findSurface(player, overworld);
            mode = "поверхность";
        } else if (undergroundRetryCount >= UNDERGROUND_CLOSE_THRESHOLD) {
            debugChat(server, "Под землёй слишком долго (" + undergroundRetryCount
                    + "x). Спавн вплотную за спиной.");
            undergroundRetryCount = 0;
            spawnPos = SpawnHelper.findBehindClose(player, overworld);
            mode = "вплотную за спиной";
        } else {
            spawnPos = SpawnHelper.findUnderground(player, overworld);
            mode = "подземный";
        }

        if (spawnPos == null) {
            if (SpawnHelper.isUnderground(player)) undergroundRetryCount++;
            debugChat(server, "Позиция не найдена (" + mode + ", retry #" + undergroundRetryCount + ")");
            return false;
        }
        undergroundRetryCount = 0;

        WandererEntity wanderer = ModEntities.WANDERER.get().create(overworld);
        if (wanderer == null) return false;

        wanderer.setTargetPlayer(player.getUUID());
        wanderer.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0f, 0f);
        overworld.addFreshEntity(wanderer);

        activeWandererEntityUUID = wanderer.getUUID();

        // Загружаем модель заранее — пока Скиталец идёт к игроку, модель успеет загрузиться
        ollamaClient.loadModel();

        debugChat(server, "Скиталец заспавнен [" + mode + "] → " + spawnPos
                + " для " + player.getName().getString() + " | загружаю модель...");
        LOGGER.info("[EncounterManager] Wanderer spawned for {} at {} ({})",
                player.getName().getString(), spawnPos, mode);
        return true;
    }
}
