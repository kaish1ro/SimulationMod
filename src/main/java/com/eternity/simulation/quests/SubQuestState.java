package com.eternity.simulation.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Прогресс промежуточных заданий ({@link SubQuestRegistry}) — общий на весь
 * мир, как и {@link SimulationQuestState} (мод под одиночную/кооп-игру).
 *
 * <p>{@code active} — задания, сейчас показанные игроку. {@code completed} —
 * уже выполненные, чтобы задание не активировалось повторно (например, при
 * повторном прохождении того же чек-пойнта).
 */
public class SubQuestState extends SavedData {

    private static final String ID = "simulation_subquest_state";

    private final Set<String> active = new LinkedHashSet<>();
    private final Set<String> completed = new LinkedHashSet<>();

    public static SubQuestState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SubQuestState::load, SubQuestState::new, ID);
    }

    public Set<String> getActive() {
        return active;
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    /** @return true если реально активировали (иначе — уже было активно или уже выполнено раньше) */
    public boolean activate(String id) {
        if (completed.contains(id) || active.contains(id)) return false;
        active.add(id);
        setDirty();
        return true;
    }

    /** @return true если реально завершили (задание было активно) */
    public boolean complete(String id) {
        if (!active.remove(id)) return false;
        completed.add(id);
        setDirty();
        return true;
    }

    /** Полный сброс (перестройка замка/отладка) — все подзадания сейчас относятся к замку. */
    public boolean resetAll() {
        boolean changed = !active.isEmpty() || !completed.isEmpty();
        active.clear();
        completed.clear();
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag activeList = new ListTag();
        for (String id : active) activeList.add(StringTag.valueOf(id));
        tag.put("active", activeList);

        ListTag completedList = new ListTag();
        for (String id : completed) completedList.add(StringTag.valueOf(id));
        tag.put("completed", completedList);
        return tag;
    }

    public static SubQuestState load(CompoundTag tag) {
        SubQuestState data = new SubQuestState();
        ListTag activeList = tag.getList("active", 8);
        for (int i = 0; i < activeList.size(); i++) data.active.add(activeList.getString(i));
        ListTag completedList = tag.getList("completed", 8);
        for (int i = 0; i < completedList.size(); i++) data.completed.add(completedList.getString(i));
        return data;
    }
}
