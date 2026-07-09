package com.eternity.simulation.quests;

import java.util.List;

/**
 * Описание одного задания системы квестов Симуляции (не FTB).
 *
 * @param deps id заданий-предпосылок (граф, как в FTB) — пусто, если задание доступно сразу.
 */
public record SimulationQuest(String id, String title, List<String> description, List<String> deps) {
}
