package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Точки возрождения замка: DATA-маркеры с ключом вида {@code Nspawnpoint}
 * (например {@code 1spawnpoint}, {@code 2spawnpoint}).
 *
 * <ul>
 *   <li>если у маркера есть {@code radius=N} — точка спавна устанавливается, когда
 *       игрок входит в радиус {@code N} блоков от маркера;</li>
 *   <li>если радиуса нет — игрок должен встать ровно на блок маркера.</li>
 * </ul>
 *
 * <p>Точка возрождения ставится на {@code marker.pos().below()}. Каждый маркер
 * активируется не более одного раза на игрока (отслеживается в persistentData
 * игрока по координатам маркера), независимо от других маркеров — в том числе
 * с тем же числовым префиксом {@code N}.
 */
public final class CastleSpawnPointTask {

    private static final Pattern SPAWNPOINT_KEY = Pattern.compile("\\d+spawnpoint");

    /** Тег persistentData игрока: CompoundTag с булевыми флагами "x_y_z" -> true. */
    private static final String NBT_ACTIVATED = "simulation_activated_spawnpoints";

    private static final int TICK_INTERVAL = 20; // раз в секунду
    private static int tickCounter = 0;

    private CastleSpawnPointTask() {}

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (level == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isCastleSpawnSystemInit()) return;

        List<CastleDataMarker> markers = data.getCastleMarkers();
        if (markers.isEmpty() || level.players().isEmpty()) return;

        for (CastleDataMarker marker : markers) {
            boolean isSpawnpoint = marker.keys().stream().anyMatch(k -> SPAWNPOINT_KEY.matcher(k).matches());
            if (!isSpawnpoint) continue;

            int radius = marker.has("radius") ? marker.getInt("radius", -1) : -1;
            BlockPos respawnPos = marker.pos().below();
            String markerKey = marker.pos().getX() + "_" + marker.pos().getY() + "_" + marker.pos().getZ();

            for (ServerPlayer player : level.players()) {
                CompoundTag activated = player.getPersistentData().getCompound(NBT_ACTIVATED);
                if (activated.getBoolean(markerKey)) continue;

                boolean reached = radius > 0
                        ? player.blockPosition().distSqr(marker.pos()) <= (double) radius * radius
                        : player.blockPosition().equals(marker.pos());

                if (!reached) continue;

                player.setRespawnPosition(level.dimension(), respawnPos, 0.0f, true, true);

                activated.putBoolean(markerKey, true);
                player.getPersistentData().put(NBT_ACTIVATED, activated);
            }
        }
    }
}
