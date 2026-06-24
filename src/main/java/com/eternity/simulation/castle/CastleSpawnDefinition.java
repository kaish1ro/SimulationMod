package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Точка спавна мобов, полученная из DATA-маркера с полем {@code mob=...}.
 *
 * <p>{@code index} — порядковый номер среди всех таких точек (стабилен, т.к.
 * порядок {@code castleMarkers} не меняется) — используется как ключ
 * персистентного состояния (триггер/живые мобы) в {@link com.eternity.simulation.SimulationSavedData}.
 */
public record CastleSpawnDefinition(
    int index,
    BlockPos pos,
    String mobId,
    int count,
    int radius,
    String groupId,
    String keyDoorId,
    boolean isFloor1Boss,
    int triggerRadius
) {

    /** Радиус приближения игрока для группы floor1 (кроме босса). */
    public static final int FLOOR1_TRIGGER_RADIUS = 40;

    /** Радиус приближения игрока для комнат/лабиринта. */
    public static final int ROOM_TRIGGER_RADIUS = 10;

    /** Радиус приближения игрока для группы blue_tower_last_floor. */
    public static final int BLUE_TOWER_LAST_FLOOR_TRIGGER_RADIUS = 7;

    public static List<CastleSpawnDefinition> fromMarkers(List<CastleDataMarker> markers) {
        List<CastleSpawnDefinition> result = new ArrayList<>();
        int index = 0;
        for (CastleDataMarker marker : markers) {
            if (!marker.has("mob")) continue;

            String mobId = marker.get("mob");
            int count = marker.getInt("count", 1);
            int radius = marker.getInt("radius", 0);
            String groupId = marker.has("id") ? marker.get("id") : null;
            String keyDoorId = marker.has("keyid") ? marker.get("keyid") : null;
            boolean isBoss = "floor1_boss".equals(groupId);

            int triggerRadius;
            if (isBoss) {
                triggerRadius = -1; // не триггерится приближением — только смертью floor1
            } else if ("floor1".equals(groupId)) {
                triggerRadius = FLOOR1_TRIGGER_RADIUS;
            } else if ("blue_tower".equals(groupId)) {
                triggerRadius = -1; // триггерится решением головоломки с пьедесталами
            } else if ("blue_tower_last_floor".equals(groupId)) {
                triggerRadius = BLUE_TOWER_LAST_FLOOR_TRIGGER_RADIUS;
            } else if (groupId != null && groupId.endsWith("_second_wave")) {
                triggerRadius = -1; // триггерится смертью группы-предшественника
            } else if ("blue_tower_boss".equals(groupId)) {
                triggerRadius = -1; // "Хранитель тайн" триггерится смертью второй волны последнего этажа синей башни
            } else if ("floor2".equals(groupId)) {
                triggerRadius = -1; // триггерится смертью undead_paladin (outside_tower2 + floor1_yellow_tower)
            } else if ("boss_fight".equals(groupId)) {
                triggerRadius = -1; // спавнится вручную через CastleBossFightTask (волны босса)
            } else if (groupId != null && (groupId.startsWith("roof_wave") || groupId.startsWith("id=roof_")
                    || "roof_second_wave".equals(groupId))) {
                triggerRadius = -1; // волны крыши: триггерятся вручную через CastleRoofSealTask
            } else {
                triggerRadius = ROOM_TRIGGER_RADIUS;
            }

            result.add(new CastleSpawnDefinition(
                index, marker.pos(), mobId, count, radius, groupId, keyDoorId, isBoss, triggerRadius));
            index++;
        }
        return result;
    }
}
