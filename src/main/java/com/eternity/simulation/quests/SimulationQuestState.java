package com.eternity.simulation.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Прогресс по квестам Симуляции — общий на весь мир (не per-player), как и
 * остальные состояния мода (это мод под одиночную/кооп-игру, не сервер на
 * тысячи игроков).
 */
public class SimulationQuestState extends SavedData {

    private static final String ID = "simulation_quest_state";

    private final Set<String> completed = new LinkedHashSet<>();

    public static SimulationQuestState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SimulationQuestState::load, SimulationQuestState::new, ID);
    }

    public Set<String> getCompleted() {
        return completed;
    }

    public boolean isCompleted(String questId) {
        return completed.contains(questId);
    }

    /** @return true если состояние реально изменилось (квест не был выполнен раньше) */
    public boolean markCompleted(String questId) {
        boolean changed = completed.add(questId);
        if (changed) setDirty();
        return changed;
    }

    /** Сбрасывает выполненность указанных квестов (перестройка замка/отладка). */
    public boolean resetQuests(java.util.Collection<String> questIds) {
        boolean changed = completed.removeAll(questIds);
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String id : completed) list.add(StringTag.valueOf(id));
        tag.put("completed", list);
        return tag;
    }

    public static SimulationQuestState load(CompoundTag tag) {
        SimulationQuestState data = new SimulationQuestState();
        ListTag list = tag.getList("completed", 8);
        for (int i = 0; i < list.size(); i++) data.completed.add(list.getString(i));
        return data;
    }
}
