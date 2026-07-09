package com.eternity.simulation.quests;

/**
 * Промежуточное задание — НЕ входит в основной граф ({@link SimulationQuestRegistry}),
 * показывается отдельной строкой под своим {@code parentQuestId} в списке (сайдбаре),
 * в графе не отображается. Активируется/завершается игровыми событиями (см.
 * задачи в пакете {@code com.eternity.simulation.castle}), а не кликом в UI.
 */
public record SubQuest(String id, String title, String parentQuestId) {
}
