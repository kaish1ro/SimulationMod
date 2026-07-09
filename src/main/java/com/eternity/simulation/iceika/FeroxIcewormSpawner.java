package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Замена биом-модификатора естественного спавна — тот оказался ненадёжным
 * (не удалось встретить Ferox Iceworm даже с завышенным весом и явным
 * списком поверхностных биомов, скорее всего мешает лимит мобов категории
 * "monster" на игрока в такой тяжёлой сборке). Вместо этого — свой триггер:
 * копим у КАЖДОГО игрока пройденное по горизонтали расстояние с момента
 * входа в Iceika; как только у кого-то накопилась 1000 блоков — спавним
 * Ferox рядом с ним (в радиусе 50 блоков, гарантированно на поверхности,
 * с проверкой места под его крупную модель) и сбрасываем счётчик у ВСЕХ
 * игроков, включая тех, кто сейчас не в Iceika.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class FeroxIcewormSpawner {

    private static final ResourceKey<Level> ICEIKA_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("divinerpg", "iceika"));
    private static final ResourceLocation FEROX_ICEWORM_ID =
            new ResourceLocation("threateningly_mobs", "ferox_iceworm");

    private static final double TRIGGER_DISTANCE = 1000.0;
    private static final int SPAWN_RADIUS_MIN = 10;
    private static final int SPAWN_RADIUS_MAX = 50;
    private static final int SPAWN_ATTEMPTS = 16;

    // Модель крупная — 3x3 по горизонтали, около 10 блоков в высоту.
    private static final int CLEARANCE_HALF_WIDTH = 1; // радиус 1 = площадка 3x3
    private static final int CLEARANCE_HEIGHT = 10;

    private static final String DISTANCE_TAG = "simulation_iceika_distance";
    private static final String LAST_X_TAG = "simulation_iceika_last_x";
    private static final String LAST_Z_TAG = "simulation_iceika_last_z";

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getTo().equals(ICEIKA_KEY)) return;
        resetTracking(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ICEIKA_KEY) return;

        CompoundTag data = player.getPersistentData();
        if (!data.contains(LAST_X_TAG)) {
            // Первый тик отслеживания (например, игрок уже был в Iceika при
            // загрузке мира, без события смены измерения) — просто фиксируем
            // точку отсчёта, без придуманного скачка расстояния.
            resetTracking(player);
            return;
        }

        double lastX = data.getDouble(LAST_X_TAG);
        double lastZ = data.getDouble(LAST_Z_TAG);
        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double delta = Math.sqrt(dx * dx + dz * dz);

        double distance = data.getDouble(DISTANCE_TAG) + delta;
        data.putDouble(LAST_X_TAG, player.getX());
        data.putDouble(LAST_Z_TAG, player.getZ());

        if (distance < TRIGGER_DISTANCE) {
            data.putDouble(DISTANCE_TAG, distance);
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) return;
        spawnNear(level, player);
        resetAllPlayers(level);
    }

    private static void resetTracking(Player player) {
        CompoundTag data = player.getPersistentData();
        data.putDouble(DISTANCE_TAG, 0.0);
        data.putDouble(LAST_X_TAG, player.getX());
        data.putDouble(LAST_Z_TAG, player.getZ());
    }

    private static void resetAllPlayers(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            resetTracking(player);
        }
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(FEROX_ICEWORM_ID);
        if (type == null) return; // мод не установлен — тихо пропускаем

        RandomSource random = level.getRandom();
        BlockPos spot = findSurfaceSpot(level, player.getX(), player.getZ(), random);
        if (spot == null) return;

        Entity entity = type.create(level);
        if (entity == null) return;
        entity.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
    }

    /** Ищет точку на поверхности (не под водой, с воздухом под модель 3x3x10) в кольце 10-50 блоков от игрока. */
    @Nullable
    private static BlockPos findSurfaceSpot(ServerLevel level, double originX, double originZ, RandomSource random) {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = SPAWN_RADIUS_MIN + random.nextDouble() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN);
            int x = (int) Math.round(originX + Math.cos(angle) * dist);
            int z = (int) Math.round(originZ + Math.sin(angle) * dist);

            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, topY - 1, z);
            BlockState groundState = level.getBlockState(ground);
            if (!groundState.getFluidState().isEmpty()) continue; // поверхность — вода, не годится
            if (!groundState.isFaceSturdy(level, ground, Direction.UP)) continue;

            BlockPos standing = ground.above();
            if (hasClearance(level, standing)) {
                return standing;
            }
        }
        return null;
    }

    private static boolean hasClearance(ServerLevel level, BlockPos base) {
        for (int dx = -CLEARANCE_HALF_WIDTH; dx <= CLEARANCE_HALF_WIDTH; dx++) {
            for (int dz = -CLEARANCE_HALF_WIDTH; dz <= CLEARANCE_HALF_WIDTH; dz++) {
                for (int dy = 0; dy < CLEARANCE_HEIGHT; dy++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) return false; // вода/лава в объёме модели
                    if (!state.getCollisionShape(level, pos).isEmpty()) return false; // твёрдый блок
                }
            }
        }
        return true;
    }
}
