package com.eternity.simulation.balance;

import net.minecraft.world.item.ItemStack;

/** Item-bound upgrade stage. Missing tag means raw drop/craft: stage 0. */
public final class WeaponUpgrade {

    private static final String TAG_STAGE = "simulation_upgrade_stage";

    private WeaponUpgrade() {}

    /** @return upgrade stage (0..7); no tag = 0, the fixed raw base. */
    public static int getStage(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_STAGE)) {
            return WeaponProgression.MIN_STAGE;
        }
        return WeaponProgression.clampStage(stack.getTag().getInt(TAG_STAGE));
    }

    public static void setStage(ItemStack stack, int stage) {
        stack.getOrCreateTag().putInt(TAG_STAGE, WeaponProgression.clampStage(stage));
    }
}
