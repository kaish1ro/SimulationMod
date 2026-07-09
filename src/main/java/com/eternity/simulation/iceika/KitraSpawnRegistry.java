package com.eternity.simulation.iceika;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Всего одна Китра на весь биом Заснежья (на деле — на всё измерение
 * Iceika): в биоме boneyard структур {@code whale_skull} очень много, и без
 * этого флага она заспавнилась бы рядом с каждой из них.
 */
public class KitraSpawnRegistry extends SavedData {

    private static final String ID = "simulation_kitra_spawned";

    private boolean spawned;

    public static KitraSpawnRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(KitraSpawnRegistry::load, KitraSpawnRegistry::new, ID);
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void markSpawned() {
        spawned = true;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("spawned", spawned);
        return tag;
    }

    public static KitraSpawnRegistry load(CompoundTag tag) {
        KitraSpawnRegistry data = new KitraSpawnRegistry();
        data.spawned = tag.getBoolean("spawned");
        return data;
    }
}
