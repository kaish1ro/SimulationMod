package com.eternity.simulation.structures;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Какие древние колодцы уже разгаданы (звезда Незера брошена в воду) — чтобы
 * нельзя было фармить фрагмент карты повторными бросками в один и тот же колодец.
 * Ключ — упакованная позиция угла bounding box структуры (стабильна для конкретного
 * сгенерированного экземпляра).
 */
public class AncientWellSolvedData extends SavedData {

    private static final String ID = "simulation_solved_ancient_wells";

    private final LongSet solved = new LongOpenHashSet();

    public static AncientWellSolvedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                AncientWellSolvedData::load, AncientWellSolvedData::new, ID);
    }

    public boolean isSolved(BlockPos originKey) {
        return solved.contains(originKey.asLong());
    }

    public void markSolved(BlockPos originKey) {
        solved.add(originKey.asLong());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("solved", solved.toLongArray());
        return tag;
    }

    public static AncientWellSolvedData load(CompoundTag tag) {
        AncientWellSolvedData data = new AncientWellSolvedData();
        for (long l : tag.getLongArray("solved")) {
            data.solved.add(l);
        }
        return data;
    }
}
