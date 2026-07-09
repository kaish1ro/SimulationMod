package com.eternity.simulation.structures;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Сколько блоков льда уже растаяло у каждой замороженной глыбы (ключ — центр
 * bounding box структуры) — и какие глыбы уже полностью обвалились (чтобы не
 * повторять обвал/проверки для уже разрушенных).
 */
public class FrozenBroodmotherProgressData extends SavedData {

    private static final String ID = "simulation_frozen_broodmother_progress";

    private final Long2IntOpenHashMap destroyedCount = new Long2IntOpenHashMap();
    private final LongSet collapsed = new LongOpenHashSet();

    public static FrozenBroodmotherProgressData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                FrozenBroodmotherProgressData::load, FrozenBroodmotherProgressData::new, ID);
    }

    public boolean isCollapsed(BlockPos key) {
        return collapsed.contains(key.asLong());
    }

    public void markCollapsed(BlockPos key) {
        collapsed.add(key.asLong());
        setDirty();
    }

    /** Увеличивает счётчик растаявших блоков для этой глыбы и возвращает новое значение. */
    public int incrementDestroyed(BlockPos key) {
        long k = key.asLong();
        int next = destroyedCount.get(k) + 1;
        destroyedCount.put(k, next);
        setDirty();
        return next;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] keys = destroyedCount.keySet().toLongArray();
        int[] values = new int[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = destroyedCount.get(keys[i]);
        tag.put("keys", new LongArrayTag(keys));
        tag.put("values", new IntArrayTag(values));
        tag.put("collapsed", new LongArrayTag(collapsed.toLongArray()));
        return tag;
    }

    public static FrozenBroodmotherProgressData load(CompoundTag tag) {
        FrozenBroodmotherProgressData data = new FrozenBroodmotherProgressData();
        long[] keys = tag.getLongArray("keys");
        int[] values = tag.getIntArray("values");
        for (int i = 0; i < keys.length && i < values.length; i++) {
            data.destroyedCount.put(keys[i], values[i]);
        }
        for (long l : tag.getLongArray("collapsed")) {
            data.collapsed.add(l);
        }
        return data;
    }
}
