package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** Возле уже поставленных башен Лича изредка появляется threateningly_mobs:snow_servent. */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class LichTowerAmbientSpawner {

    private static final ResourceKey<Level> ICEIKA_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("divinerpg", "iceika"));
    private static final ResourceLocation SNOW_SERVENT_ID =
            new ResourceLocation("threateningly_mobs", "snow_servent");

    private static final int CHECK_INTERVAL = 200; // раз в ~10 секунд на башню
    private static final float SPAWN_CHANCE = 0.05F; // "иногда" — не при каждой проверке

    private static final int SPAWN_RADIUS_MIN = 20;
    private static final int SPAWN_RADIUS_MAX = 40;
    private static final int CLEARANCE_HALF_WIDTH = 0; // 1x1 по горизонтали достаточно
    private static final int CLEARANCE_HEIGHT = 3;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ICEIKA_KEY)) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;

        RandomSource random = level.getRandom();
        for (BlockPos tower : LichRegistry.get(level).getAll()) {
            if (!level.hasChunk(tower.getX() >> 4, tower.getZ() >> 4)) continue; // никого рядом — не тратим время
            if (random.nextFloat() >= SPAWN_CHANCE) continue;
            trySpawn(level, tower, random);
        }
    }

    private static void trySpawn(ServerLevel level, BlockPos tower, RandomSource random) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(SNOW_SERVENT_ID);
        if (type == null) return;

        BlockPos spot = findSurfaceSpot(level, tower.getX(), tower.getZ(), random);
        if (spot == null) return;

        Entity entity = type.create(level);
        if (entity == null) return;
        entity.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
    }

    @Nullable
    private static BlockPos findSurfaceSpot(ServerLevel level, int originX, int originZ, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = SPAWN_RADIUS_MIN + random.nextDouble() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN);
            int x = (int) Math.round(originX + Math.cos(angle) * dist);
            int z = (int) Math.round(originZ + Math.sin(angle) * dist);

            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, topY - 1, z);
            BlockState groundState = level.getBlockState(ground);
            if (!groundState.getFluidState().isEmpty()) continue;

            BlockPos standing = ground.above();
            if (hasClearance(level, standing)) return standing;
        }
        return null;
    }

    private static boolean hasClearance(ServerLevel level, BlockPos base) {
        for (int dx = -CLEARANCE_HALF_WIDTH; dx <= CLEARANCE_HALF_WIDTH; dx++) {
            for (int dz = -CLEARANCE_HALF_WIDTH; dz <= CLEARANCE_HALF_WIDTH; dz++) {
                for (int dy = 0; dy < CLEARANCE_HEIGHT; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) return false;
                    if (!state.getCollisionShape(level, pos).isEmpty()) return false;
                }
            }
        }
        return true;
    }
}
