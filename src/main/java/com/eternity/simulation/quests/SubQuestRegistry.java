package com.eternity.simulation.quests;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр всех промежуточных заданий мода. Метаданные (title/parentQuestId) не
 * гоняются по сети — клиент знает их отсюда же, с сервера синхронизируется
 * только список активных id (см. {@code SyncActiveSubQuestsPacket}).
 */
public final class SubQuestRegistry {

    private SubQuestRegistry() {}

    /** Ключи лабиринта — соответствие чек-пойнт → door_id см. в CastleSubQuestTask. */
    public static final String KEY_LABYRINTH_ROOM_1 = "key_labyrinth_room_1";
    public static final String KEY_LABYRINTH_ROOM_2 = "key_labyrinth_room_2";
    public static final String KEY_LABYRINTH_ROOM_3 = "key_labyrinth_room_3";

    /** Первый этаж замка: стражи (floor1) → паладин (floor1_boss). */
    public static final String FLOOR1_KILL_GUARDS  = "floor1_kill_guards";
    public static final String FLOOR1_KILL_PALADIN = "floor1_kill_paladin";

    /** Синяя башня: головоломка пьедесталов → подъём наверх → Хранитель тайн. */
    public static final String BLUE_TOWER_PUZZLE = "blue_tower_puzzle";
    public static final String BLUE_TOWER_CLIMB  = "blue_tower_climb";
    public static final String BLUE_TOWER_KILL_GUARDIAN = "blue_tower_kill_guardian";

    /** Внешние башни: ключ от жёлтых башен (падает с паладина last_tower_room/floor1_yellow_tower). */
    public static final String KEY_YELLOW_TOWERS = "key_yellow_towers";

    /** Жёлтые башни: подъём на 2-й этаж → стражники → Хелвар. */
    public static final String FLOOR2_CLIMB       = "floor2_climb";
    public static final String FLOOR2_KILL_GUARDS = "floor2_kill_guards";
    public static final String FLOOR2_KILL_BOSS   = "floor2_kill_boss";

    /** Снятие защитного поля: маяк → 3 волны стражей. */
    public static final String SHIELD_PLACE_BEACON = "shield_place_beacon";
    public static final String SHIELD_WAVE1_KILL   = "shield_wave1_kill";
    public static final String SHIELD_WAVE2_KILL   = "shield_wave2_kill";
    public static final String SHIELD_WAVE3_KILL   = "shield_wave3_kill";

    /**
     * Не настоящее подзадание — служебный id для передачи процента прогресса
     * "снятия поля" (0=маяк поставлен, 100=крышка снесена и поле пошло по бокам)
     * через тот же канал счётчиков (см. SyncSubQuestCountsPacket), чтобы не
     * заводить отдельный пакет ради одного числа.
     */
    public static final String SHIELD_PROGRESS_PERCENT = "shield_progress_percent";

    private static final List<SubQuest> ALL = List.of(
        new SubQuest(KEY_LABYRINTH_ROOM_1, "Найти ключ", "labyrinth_exit"),
        new SubQuest(KEY_LABYRINTH_ROOM_2, "Найти ключ", "labyrinth_exit"),
        new SubQuest(KEY_LABYRINTH_ROOM_3, "Найти ключ", "labyrinth_exit"),

        new SubQuest(FLOOR1_KILL_GUARDS,  "Убить всех стражей", "floor1_inspect"),
        new SubQuest(FLOOR1_KILL_PALADIN, "Убить паладина",     "floor1_inspect"),

        new SubQuest(BLUE_TOWER_PUZZLE, "Решите головоломку",       "blue_tower_inspect"),
        new SubQuest(BLUE_TOWER_CLIMB,  "Поднимитесь наверх",       "blue_tower_inspect"),
        new SubQuest(BLUE_TOWER_KILL_GUARDIAN, "Убейте хранителя тайн", "blue_tower_inspect"),

        new SubQuest(KEY_YELLOW_TOWERS, "Найдите ключ от жёлтых башен замка", "outer_towers_inspect"),

        new SubQuest(FLOOR2_CLIMB,       "Поднимитесь на второй этаж замка",        "yellow_towers_enter"),
        new SubQuest(FLOOR2_KILL_GUARDS, "Убейте всех стражников на втором этаже", "floor2_inspect"),
        new SubQuest(FLOOR2_KILL_BOSS,   "Победите Рыцаря преисподней Хелвара",    "floor2_inspect"),

        new SubQuest(SHIELD_PLACE_BEACON, "Поставьте маяк на алтарь посередине", "shield_removal"),
        new SubQuest(SHIELD_WAVE1_KILL,   "Убейте всех стражей", "shield_removal"),
        new SubQuest(SHIELD_WAVE2_KILL,   "Убейте всех стражей", "shield_removal"),
        new SubQuest(SHIELD_WAVE3_KILL,   "Убейте всех стражей", "shield_removal")
    );

    private static final Map<String, SubQuest> BY_ID = new LinkedHashMap<>();
    static {
        for (SubQuest sq : ALL) BY_ID.put(sq.id(), sq);
    }

    public static SubQuest byId(String id) {
        return BY_ID.get(id);
    }
}
