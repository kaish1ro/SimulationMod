package com.eternity.simulation.entity;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Спавн Наблюдателя (Питера) в первой деревне оверворлда.
 * Происходит один раз за жизнь мира — при первом входе любого игрока.
 * <p>
 * Класс подписывается через {@link com.eternity.simulation.ModEvents}.
 */
public class ObserverSpawnHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        SimulationSavedData data = SimulationSavedData.get(overworld);
        if (data.isObserverSpawned()) return;

        // Ищем ближайшую деревню вокруг точки спавна мира (радиус 30 чанков = ~480 блоков)
        BlockPos worldSpawn = overworld.getSharedSpawnPos();
        BlockPos villagePos = overworld.findNearestMapStructure(
            StructureTags.VILLAGE, worldSpawn, 30, false
        );

        BlockPos spawnPos = (villagePos != null) ? villagePos : worldSpawn;
        int y = overworld.getHeight(Heightmap.Types.WORLD_SURFACE, spawnPos.getX(), spawnPos.getZ());

        ObserverEntity observer = ModEntities.OBSERVER.get().create(overworld);
        if (observer == null) {
            LOGGER.error("[Simulation] Failed to create ObserverEntity");
            return;
        }

        observer.moveTo(spawnPos.getX() + 0.5, y, spawnPos.getZ() + 0.5, 0f, 0f);
        overworld.addFreshEntity(observer);

        data.setObserverSpawned(true);

        LOGGER.info("[Simulation] Observer (Peter) spawned at {}", spawnPos);
    }
}
