package com.eternity.simulation.quests;

import java.util.List;

/**
 * Список заданий системы квестов Симуляции — статический реестр (JSON/датапак
 * пока не нужен, заданий мало и они жёстко завязаны на кастомный код Финального
 * замка). Порядок в списке — порядок отображения в UI.
 */
public final class SimulationQuestRegistry {

    private SimulationQuestRegistry() {}

    public static final List<SimulationQuest> QUESTS = List.of(
        new SimulationQuest(
            "labyrinth_exit",
            "Найдите выход из лабиринта",
            List.of(
                "Коридоры повторяются. Это не совпадение.",
                "Выход существует — его нужно найти."
            ),
            List.of()
        ),
        new SimulationQuest(
            "floor1_inspect",
            "Осмотрите первый этаж",
            List.of(
                "Лабиринт был лишь порогом.",
                "Здесь есть те, кто охраняет тишину — и тот, кто ею командует."
            ),
            List.of("labyrinth_exit")
        ),
        new SimulationQuest(
            "blue_tower_inspect",
            "Осмотрите синюю башню",
            List.of(
                "Башня заперта не просто так.",
                "Ключ уже у тебя — осталось найти, что он открывает."
            ),
            List.of("floor1_inspect")
        ),
        new SimulationQuest(
            "castle_exit_search",
            "Найдите выход из замка",
            List.of(
                "Хранитель тайн молчит навсегда.",
                "Но замок больше, чем одна башня."
            ),
            List.of("blue_tower_inspect")
        ),
        new SimulationQuest(
            "outer_towers_inspect",
            "Осмотрите внешние башни",
            List.of(
                "За стенами — ещё стены.",
                "Кто-то охраняет и их."
            ),
            List.of("castle_exit_search")
        ),
        new SimulationQuest(
            "yellow_towers_enter",
            "Войдите в жёлтые башни",
            List.of(
                "Ключ подошёл. Дверь открыта.",
                "То, что внутри, не обрадуется гостю."
            ),
            List.of("outer_towers_inspect")
        ),
        new SimulationQuest(
            "floor2_inspect",
            "Осмотрите второй этаж",
            List.of(
                "Двери за спиной закрылись.",
                "Здесь тоже есть те, кто охраняет тишину — и тот, кто ею командует."
            ),
            List.of("yellow_towers_enter")
        ),
        new SimulationQuest(
            "roof_inspect",
            "Осмотрите крышу",
            List.of(
                "Рыцарь преисподней пал.",
                "Но над замком всё ещё горит чужой свет."
            ),
            List.of("floor2_inspect")
        ),
        new SimulationQuest(
            "shield_removal",
            "Снимите защитное поле замка",
            List.of(
                "Поле держится на чём-то одном.",
                "Найди алтарь — и лиши его силы."
            ),
            List.of("roof_inspect")
        ),
        new SimulationQuest(
            "castle_escape",
            "Выберитесь из замка",
            List.of(
                "Поле снято. Замок больше не держит тебя.",
                "Пора уходить."
            ),
            List.of("shield_removal")
        )
    );

    public static SimulationQuest byId(String id) {
        for (SimulationQuest q : QUESTS) {
            if (q.id().equals(id)) return q;
        }
        return null;
    }
}
