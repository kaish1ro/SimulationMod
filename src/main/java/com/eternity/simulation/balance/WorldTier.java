package com.eternity.simulation.balance;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.quests.SimulationQuestState;
import net.minecraft.server.MinecraftServer;

/**
 * Единая шкала мировых тиров (0..5) — один и тот же тир одновременно определяет
 * множитель урона капнутого стороннего оружия/брони И (в будущем) множитель
 * здоровья/урона боссов того же тира. См. {@code equipment_balance_v2.xlsx}.
 *
 * <p>Порядок событий скорректирован под реальную прогрессию мода (в отличие от
 * исходной таблицы): дракон в {@code ModEvents.onEntityTravelToDimension}
 * физически гейтит ВСЕ модовые измерения, поэтому "доп. мир" не может идти
 * раньше дракона — Wither, наоборот, ничем не гейтится и доступен всегда.
 *
 * <ol start="1">
 *   <li>Ванильный потолок (netherite/diamond) — базовый тир, действует всегда.</li>
 *   <li>Убит Wither.</li>
 *   <li>Убит Ender Dragon (открывает TF и остальные измерения).</li>
 *   <li>Пройден хотя бы один доп. мир ПОСЛЕ дракона (Undergarden и т.п.).</li>
 *   <li>Пройден эндгейм Simulation (квест {@code castle_escape}).</li>
 * </ol>
 */
public final class WorldTier {

    private WorldTier() {}

    public record TierStats(double damageMult, double abilityMult, double speedMult) {}

    private static final TierStats[] TIERS = {
        null, // индекс 0 не используется, тиры нумеруются с 1
        new TierStats(1.00, 0.40, 1.00), // 1 — ванильный потолок
        new TierStats(1.25, 0.55, 1.00), // 2 — убит Wither
        new TierStats(1.55, 0.70, 1.00), // 3 — убит Ender Dragon
        new TierStats(1.90, 0.85, 1.05), // 4 — доп. мир после дракона
        new TierStats(2.40, 1.00, 1.10), // 5 — эндгейм Simulation
    };

    /** @return текущий мировой тир (1..5) — вычисляется от существующих флагов прогресса, ничего не хранит отдельно. */
    public static int compute(MinecraftServer server) {
        var overworld = server.overworld();
        if (SimulationQuestState.get(overworld).isCompleted("castle_escape")) return 5;

        SimulationSavedData data = SimulationSavedData.get(overworld);
        if (data.isExtraDimensionExplored()) return 4;
        if (data.isDragonDefeated()) return 3;
        if (data.isWitherDefeated()) return 2;
        return 1;
    }

    public static TierStats stats(int tier) {
        return TIERS[Math.max(1, Math.min(5, tier))];
    }
}
