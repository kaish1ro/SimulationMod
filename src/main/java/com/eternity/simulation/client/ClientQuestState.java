package com.eternity.simulation.client;

import com.eternity.simulation.quests.SimulationQuest;
import com.eternity.simulation.quests.SimulationQuestRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Клиентский кэш прогресса квестов Симуляции — обновляется пакетом
 * {@link com.eternity.simulation.network.SyncQuestStatePacket}. QuestScreen
 * читает отсюда, а не ходит на сервер напрямую (тот же паттерн, что и
 * getsaves/gettracks в CustomMenuScreen — синхронный отклик из уже известных
 * клиенту данных).
 */
@OnlyIn(Dist.CLIENT)
public final class ClientQuestState {

    private static final Set<String> completed = new HashSet<>();
    private static final Set<String> activeSubQuestIds = new HashSet<>();
    private static final Map<String, Integer> subQuestCounts = new HashMap<>();

    /**
     * false до первого попадания в перестроенный замок, и снова false после того,
     * как поле снято и игрок отошёл дальше 100 блоков от маяка (см.
     * {@code SyncQuestUiVisibilityPacket}). Пока false — HUD не рисуется,
     * а клавиша открытия меню не работает (и меню закрывается, если было открыто).
     */
    private static boolean uiVisible = false;

    private ClientQuestState() {}

    public static void applyUiVisibility(boolean visible) {
        uiVisible = visible;
        if (!visible) QuestScreen.closeIfOpen();
    }

    public static boolean isUiVisible() {
        return uiVisible;
    }

    public static void applySync(List<String> completedQuestIds) {
        completed.clear();
        completed.addAll(completedQuestIds);
        QuestScreen.pushRefreshIfOpen();
    }

    public static boolean isCompleted(String questId) {
        return completed.contains(questId);
    }

    public static void applySubQuestSync(List<String> activeIds) {
        activeSubQuestIds.clear();
        activeSubQuestIds.addAll(activeIds);
        QuestScreen.pushRefreshIfOpen();
    }

    public static Set<String> getActiveSubQuestIds() {
        return activeSubQuestIds;
    }

    public static void applySubQuestCounts(Map<String, Integer> counts) {
        subQuestCounts.clear();
        subQuestCounts.putAll(counts);
        // Лёгкое обновление, БЕЗ пересборки дерева/сайдбара (см. pushRefreshIfOpen) —
        // счётчики меняются часто (раз в секунду, пока жива волна/этаж), полный
        // ре-рендер на каждое изменение выглядел как непрерывное "мигание" окна.
        QuestScreen.pushCountsUpdateIfOpen();
    }

    /** @return число живых мобов для подзадания, или -1 если для него нет счётчика. */
    public static int getSubQuestCount(String subQuestId) {
        return subQuestCounts.getOrDefault(subQuestId, -1);
    }

    /**
     * @return текущее задание — первое невыполненное из графа, у которого выполнены
     * все зависимости, или {@code null} если всё выполнено (или список ещё не пришёл).
     * Используется HUD-оверлеем: остальные задания (не дошли/уже прошли) ему не нужны.
     */
    public static SimulationQuest getCurrentQuest() {
        for (SimulationQuest q : SimulationQuestRegistry.QUESTS) {
            if (isCompleted(q.id())) continue;
            if (q.deps().stream().allMatch(ClientQuestState::isCompleted)) return q;
            return null; // цепочка линейная — дальше по списку идут ещё не открытые
        }
        return null;
    }
}
