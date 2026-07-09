package com.eternity.simulation.structures;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Все уже сгенерированные «по требованию» арены Цветка пустоты в Undergarden —
 * чтобы повторный крафт карты рядом с уже обслуженной областью указывал на ТУ ЖЕ
 * арену, а не плодил новую при каждом крафте.
 */
public class VoidBlossomRegistry extends SavedData {

    private static final String ID = "simulation_void_blossom_arenas";

    private final LongList positions = new LongArrayList();

    public static VoidBlossomRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VoidBlossomRegistry::load, VoidBlossomRegistry::new, ID);
    }

    public List<BlockPos> getAll() {
        List<BlockPos> result = new ArrayList<>(positions.size());
        for (long l : positions) result.add(BlockPos.of(l));
        return result;
    }

    public void add(BlockPos pos) {
        positions.add(pos.asLong());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("positions", new LongArrayTag(positions.toLongArray()));
        return tag;
    }

    public static VoidBlossomRegistry load(CompoundTag tag) {
        VoidBlossomRegistry data = new VoidBlossomRegistry();
        for (long l : tag.getLongArray("positions")) {
            data.positions.add(l);
        }
        return data;
    }
}
