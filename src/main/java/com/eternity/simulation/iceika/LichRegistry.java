package com.eternity.simulation.iceika;

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
 * Все уже сгенерированные «по требованию» башни Лича в Iceika — чтобы
 * повторный крафт карты рядом с уже обслуженной областью указывал на ТУ ЖЕ
 * башню, а не плодил новую при каждом крафте. Аналог {@code VoidBlossomRegistry}.
 */
public class LichRegistry extends SavedData {

    private static final String ID = "simulation_lich_towers";

    private final LongList positions = new LongArrayList();

    public static LichRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LichRegistry::load, LichRegistry::new, ID);
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

    public void remove(BlockPos pos) {
        positions.rem(pos.asLong());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("positions", new LongArrayTag(positions.toLongArray()));
        return tag;
    }

    public static LichRegistry load(CompoundTag tag) {
        LichRegistry data = new LichRegistry();
        for (long l : tag.getLongArray("positions")) {
            data.positions.add(l);
        }
        return data;
    }
}
