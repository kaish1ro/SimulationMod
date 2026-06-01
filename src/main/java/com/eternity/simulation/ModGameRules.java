package com.eternity.simulation;

import net.minecraft.world.level.GameRules;

public class ModGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> BLUEPRINT_GROUP1 =
        GameRules.register("simulationGroup1", GameRules.Category.MISC,
            GameRules.BooleanValue.create(false));

    public static final GameRules.Key<GameRules.BooleanValue> BLUEPRINT_GROUP2 =
        GameRules.register("simulationGroup2", GameRules.Category.MISC,
            GameRules.BooleanValue.create(false));

    public static final GameRules.Key<GameRules.BooleanValue> BLUEPRINT_GROUP3 =
        GameRules.register("simulationGroup3", GameRules.Category.MISC,
            GameRules.BooleanValue.create(false));
}
