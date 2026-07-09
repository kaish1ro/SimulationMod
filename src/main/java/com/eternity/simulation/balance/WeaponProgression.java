package com.eternity.simulation.balance;

/**
 * Item-bound weapon upgrade curve from equipment_balance_v4.xlsx.
 *
 * <p>This is intentionally separate from {@link WorldTier}: world progress can
 * scale bosses and encounters, while weapon power only changes when the exact
 * item stack is upgraded by the player.
 */
public final class WeaponProgression {

    public static final int MIN_STAGE = 0;
    public static final int MAX_STAGE = 7;

    private static final StageStats[] STAGES = {
        new StageStats(1.00, 0.40, 1.00), // 0 - post-vanilla base cap
        new StageStats(1.30, 0.50, 1.00), // 1 - Twilight Forest
        new StageStats(1.55, 0.60, 1.00), // 2 - Undergarden / Iceika
        new StageStats(1.85, 0.70, 1.02), // 3 - Aether / Blue Skies / Voidscape
        new StageStats(2.20, 0.80, 1.02), // 4 - Deeper & Darker / Mines
        new StageStats(2.60, 0.88, 1.04), // 5 - Divine RPG
        new StageStats(3.10, 0.95, 1.06), // 6 - The Midnight
        new StageStats(3.70, 1.00, 1.10), // 7 - Simulation / God
    };

    private WeaponProgression() {}

    public record StageStats(double damageMult, double abilityMult, double speedMult) {}

    public static int clampStage(int stage) {
        return Math.max(MIN_STAGE, Math.min(MAX_STAGE, stage));
    }

    public static StageStats stats(int stage) {
        return STAGES[clampStage(stage)];
    }
}
