package com.eternity.simulation.iceika;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Незавершённые встречи в башне Лича: пока живы бледные лучники со 2 и 3
 * этажей — Лич не появляется. Хранится персистентно (переживает
 * перезагрузку сервера), проверяется тиком в {@link LichEncounterManager}.
 */
public class LichEncounterRegistry extends SavedData {

    private static final String ID = "simulation_lich_encounters";

    /** Одна активная башня, ожидающая гибели всех лучников. */
    public static final class PendingEncounter {
        public final BlockPos lichSpawnPos;
        public final List<UUID> archers;

        public PendingEncounter(BlockPos lichSpawnPos, List<UUID> archers) {
            this.lichSpawnPos = lichSpawnPos;
            this.archers = archers;
        }
    }

    private final List<PendingEncounter> pending = new ArrayList<>();

    public static LichEncounterRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LichEncounterRegistry::load, LichEncounterRegistry::new, ID);
    }

    public List<PendingEncounter> getPending() {
        return pending;
    }

    public void add(PendingEncounter encounter) {
        pending.add(encounter);
        setDirty();
    }

    public void remove(PendingEncounter encounter) {
        pending.remove(encounter);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (PendingEncounter encounter : pending) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("lichSpawnPos", encounter.lichSpawnPos.asLong());
            ListTag archerList = new ListTag();
            for (UUID id : encounter.archers) {
                archerList.add(NbtUtils.createUUID(id));
            }
            entry.put("archers", archerList);
            list.add(entry);
        }
        tag.put("pending", list);
        return tag;
    }

    public static LichEncounterRegistry load(CompoundTag tag) {
        LichEncounterRegistry data = new LichEncounterRegistry();
        for (Tag entryTag : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            BlockPos lichSpawnPos = BlockPos.of(entry.getLong("lichSpawnPos"));
            List<UUID> archers = new ArrayList<>();
            for (Tag archerTag : entry.getList("archers", Tag.TAG_INT_ARRAY)) {
                archers.add(NbtUtils.loadUUID(archerTag));
            }
            data.pending.add(new PendingEncounter(lichSpawnPos, archers));
        }
        return data;
    }
}
