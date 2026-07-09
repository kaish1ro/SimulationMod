package com.eternity.simulation.structures;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Арена Цветка пустоты появляется НЕ через обычный ворлдген, а только по
 * требованию — в момент крафта карты (см. {@link VoidBlossomMapCraftListener}).
 * Так на неё нельзя случайно наткнуться при исследовании мира.
 *
 * <p>Сначала проверяем {@link VoidBlossomRegistry} — если рядом с точкой поиска
 * уже стоит сгенерированная арена, отдаём её координаты без повторной генерации.
 * Если нет — подбираем случайную точку в радиусе, проверяем по фактическим
 * блокам жидкости (а не по тегу биома — море в Undergarden может затекать и в
 * формально «не морские» биомы) и ставим шаблон прямо в код, через
 * {@code StructureTemplate.placeInWorld}.
 */
public final class VoidBlossomSpawner {

    private static final ResourceLocation TEMPLATE_ID =
            new ResourceLocation(SimulationMod.MODID, "undergarden_void_blossom");

    // Босс из мода "Bosses of Mass Destruction" — сама арена никак с этим модом
    // не связана, просто спавним его сущность в центре после установки шаблона.
    private static final ResourceLocation BOSS_ID =
            new ResourceLocation("bosses_of_mass_destruction", "void_blossom");

    private static final int MIN_DISTANCE_BLOCKS = 1500;
    private static final int MAX_DISTANCE_BLOCKS = 2000;
    private static final int REUSE_RADIUS_BLOCKS = 2000;  // в этом радиусе ищем уже существующую арену
    private static final int MAX_PLACEMENT_ATTEMPTS = 24; // часть точек теперь отсеивается по воде

    // Верх арены — фиксированно чуть выше уровня моря (а не «куда дотянется скан»)
    private static final int TOP_OFFSET_ABOVE_SEA_LEVEL = 10;

    private VoidBlossomSpawner() {}

    /** Возвращает позицию арены (существующей рядом или только что сгенерированной), либо null при неудаче. */
    @Nullable
    public static BlockPos findOrCreateArena(ServerLevel level, BlockPos origin, RandomSource random) {
        VoidBlossomRegistry registry = VoidBlossomRegistry.get(level);

        double reuseRadiusSqr = (double) REUSE_RADIUS_BLOCKS * REUSE_RADIUS_BLOCKS;
        BlockPos nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;
        for (BlockPos existing : registry.getAll()) {
            double distSqr = existing.distSqr(origin);
            if (distSqr <= reuseRadiusSqr && distSqr < nearestDistSqr) {
                nearest = existing;
                nearestDistSqr = distSqr;
            }
        }
        if (nearest != null) return nearest;

        Optional<StructureTemplate> templateOpt = level.getServer().getStructureManager().get(TEMPLATE_ID);
        if (templateOpt.isEmpty()) return null;
        StructureTemplate template = templateOpt.get();
        Vec3i size = template.getSize();

        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            BlockPos candidate = pickCandidatePosition(level, origin, random, size);
            if (candidate == null) continue;
            if (footprintTouchesWater(level, candidate, size)) continue;

            placeTemplate(level, template, candidate);
            registry.add(candidate);
            spawnBoss(level, candidate, size);
            return candidate;
        }
        return null;
    }

    /** Спавнит босса в центре арены на уровне пола полости (не в толще монолита). */
    private static void spawnBoss(ServerLevel level, BlockPos origin, Vec3i size) {
        EntityType<?> bossType = ForgeRegistries.ENTITY_TYPES.getValue(BOSS_ID);
        if (bossType == null) return; // мод босса не установлен — тихо пропускаем

        int centerX = origin.getX() + size.getX() / 2;
        int centerZ = origin.getZ() + size.getZ() / 2;
        int floorY = findFloorY(level, centerX, origin.getY(), origin.getY() + size.getY(), centerZ);
        if (floorY == Integer.MIN_VALUE) return;

        Entity entity = bossType.create(level);
        if (entity == null) return;
        entity.moveTo(centerX + 0.5, floorY, centerZ + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
    }

    /**
     * Арена — монолит с вырезанной сферической полостью (см. историю правки
     * NBT), поэтому центральная колонна снизу вверх идёт: сплошная порода →
     * воздух полости. Первый воздушный блок и есть пол, на котором должен
     * стоять игрок/босс.
     */
    private static int findFloorY(ServerLevel level, int x, int minY, int maxY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, minY, z);
        for (int y = minY; y <= maxY; y++) {
            pos.setY(y);
            if (level.getBlockState(pos).isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    @Nullable
    private static BlockPos pickCandidatePosition(ServerLevel level, BlockPos origin, RandomSource random, Vec3i size) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_DISTANCE_BLOCKS + random.nextDouble() * (MAX_DISTANCE_BLOCKS - MIN_DISTANCE_BLOCKS);
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);

        int minY = level.getMinBuildHeight();
        int seaLevel = level.getChunkSource().getGenerator().getSeaLevel();
        int standY = seaLevel + TOP_OFFSET_ABOVE_SEA_LEVEL - size.getY();
        if (standY < minY) return null;

        return new BlockPos(x, standY, z);
    }

    /**
     * Проверка по реальным блокам жидкости, а не по биому: в Undergarden море
     * может выступать за пределы биомов, помеченных как «морские», поэтому
     * биом-фильтр пропускал точки, реально залитые водой. Сканируем весь
     * периметр footprint'а арены по всей её высоте — если хоть один блок на
     * границе жидкий, точка отбраковывается.
     */
    private static boolean footprintTouchesWater(ServerLevel level, BlockPos origin, Vec3i size) {
        int sizeX = size.getX();
        int sizeZ = size.getZ();
        int minY = origin.getY();
        int maxY = origin.getY() + size.getY();

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                boolean onPerimeter = x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1;
                if (!onPerimeter) continue;

                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.getX() + x, minY, origin.getZ() + z);
                for (int y = minY; y <= maxY; y++) {
                    pos.setY(y);
                    if (level.getFluidState(pos).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void placeTemplate(ServerLevel level, StructureTemplate template, BlockPos pos) {
        Vec3i size = template.getSize();
        // Прогружаем/генерируем все чанки, которые займёт арена (67×37×67 легко
        // перекрывает несколько чанков) — без этого setBlock попадёт в негенерированные
        // чанки и тихо потеряется.
        int minChunkX = (pos.getX()) >> 4;
        int maxChunkX = (pos.getX() + size.getX()) >> 4;
        int minChunkZ = (pos.getZ()) >> 4;
        int maxChunkZ = (pos.getZ() + size.getZ()) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }

        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE);
        template.placeInWorld(level, pos, pos, settings, level.getRandom(), 2);
    }
}
