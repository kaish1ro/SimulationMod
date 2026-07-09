package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Башня Лича появляется НЕ через обычный ворлдген, а по требованию — в
 * момент крафта карты (см. {@link LichMapCraftListener}), та же схема, что
 * и у Цветка пустоты ({@code VoidBlossomSpawner}).
 *
 * <p>История правок высоты:
 * <ul>
 *   <li>Сначала фильтровали по списку "поверхностных" биомов — не сработало,
 *   у Iceika биом зависит от Y (multi_noise), а не только от XZ.</li>
 *   <li>Потом взяли {@code WORLD_SURFACE} для высоты + строгий {@code
 *   canSeeSky} — из-за WORLD_SURFACE (считает и листву тоже) башня один раз
 *   встала прямо на верхушку дерева, а строгий canSeeSky почти всегда
 *   проваливался в лесных биомах Iceika (там очень много деревьев), из-за
 *   чего раньше существовал "насильный" фолбэк без проверки неба вовсе —
 *   он и утопил башню под землю в тот раз.</li>
 * </ul>
 * Итоговый подход: {@code MOTION_BLOCKING_NO_LEAVES} — видит землю СКВОЗЬ
 * листву (настоящий грунт, не верхушка дерева), без требования "видит небо"
 * (лес — это нормально, деревья и так вырубаем). Проверяем только что это не
 * вода и не глубокая пещера/навес (по разнице WORLD_SURFACE - MOTION_BLOCKING,
 * т.е. над грунтом не больше типичного дерева). После установки шаблона —
 * заливаем ровную площадку из {@code divinerpg:frozen_grass} под и вокруг
 * башни (чтобы стояла ровно на неровном рельефе) и вырубаем деревья/листву
 * в буфере вокруг.
 */
public final class LichTowerSpawner {

    private static final ResourceLocation TEMPLATE_ID =
            new ResourceLocation(SimulationMod.MODID, "lich_tower");
    private static final ResourceLocation FROZEN_GRASS_ID =
            new ResourceLocation("divinerpg", "frozen_grass");
    private static final ResourceLocation FROZEN_DIRT_ID =
            new ResourceLocation("divinerpg", "frozen_dirt");

    private static final int REUSE_RADIUS_BLOCKS = 2000;
    private static final int ATTEMPTS_PER_RING = 20;
    private static final int[] RING_RADII = {200, 600, 1200, 2000, 3000, 4500, 6500, 9000};

    // Разница WORLD_SURFACE - MOTION_BLOCKING_NO_LEAVES больше этого — уже не
    // дерево над землёй, а что-то вроде навеса/горы (отбраковываем).
    private static final int MAX_CANOPY_HEIGHT = 24;

    private static final int PLATFORM_MARGIN = 3;   // площадка шире самой башни на столько блоков
    private static final int TURF_LAYERS = 1;        // верхний слой — дёрн (frozen_grass)
    private static final int CORE_DIRT_LAYERS = 2;   // под дёрном — земля, ставим безусловно (гарантия ровной площадки)
    private static final int EXTRA_DIRT_LAYERS = 10; // ещё ниже — та же земля, но только по воздуху/снегу (не ломаем рельеф)
    private static final int CLEAR_HEIGHT = 30;      // на сколько блоков вверх расчищаем над площадкой
    private static final int TREE_CLEAR_MARGIN = 5;  // доп. вырубка деревьев/листвы за пределами площадки

    private LichTowerSpawner() {}

    @Nullable
    public static BlockPos findOrCreateArena(ServerLevel level, BlockPos origin, RandomSource random) {
        LichRegistry registry = LichRegistry.get(level);

        Optional<StructureTemplate> templateOpt = level.getServer().getStructureManager().get(TEMPLATE_ID);
        if (templateOpt.isEmpty()) return null;
        StructureTemplate template = templateOpt.get();
        Vec3i size = template.getSize();

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
        if (nearest != null) {
            // Более ранние версии поиска могли сохранить сюда битую (подземную)
            // точку — не переиспользуем вслепую, иначе старый баг воспроизводится
            // вечно при каждом следующем крафте. Проверяем небо НАД верхушкой
            // башни (base + высота шаблона), а не над базой: над базой стоит
            // сама башня, там небо не видно и у корректно поставленной.
            BlockPos aboveTop = nearest.above(size.getY() + 1);
            if (level.canSeeSky(aboveTop)) return nearest;
            registry.remove(nearest);
        }

        for (int ring = 0; ring < RING_RADII.length; ring++) {
            int minR = ring == 0 ? 100 : RING_RADII[ring - 1];
            int maxR = RING_RADII[ring];

            for (int attempt = 0; attempt < ATTEMPTS_PER_RING; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = minR + random.nextDouble() * (maxR - minR);
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);

                if (!isValidFootprint(level, x, z, size)) continue;

                int groundY = computeGroundY(level, x, z, size);
                BlockPos pos = new BlockPos(x, groundY, z);

                levelPlatformAndClearTrees(level, x, z, size, groundY);
                placeTemplate(level, template, pos);
                registry.add(pos);
                LichEncounterManager.startEncounter(level, pos);
                return pos;
            }
        }
        return null;
    }

    // Замёрзший океан/река Iceika покрыты сплошным льдом, под которым вода —
    // проверять только верхний блок мало (лёд не жидкость, проверка проходит).
    // Сканируем вниз от поверхности: нашли воду под коркой — это океан/река,
    // отбраковываем. По ТЕГУ льда НЕ отбраковываем — на валидной ледяной суше
    // (биом ice_spikes) лёд на поверхности нормален; топит башню именно вода.
    private static final int WATER_SCAN_DEPTH = 12;

    /** Проверяет по 4 углам и центру: нет воды под поверхностью (в т.ч. подо льдом) и над грунтом не больше дерева. */
    private static boolean isValidFootprint(ServerLevel level, int originX, int originZ, Vec3i size) {
        int[] xs = {originX, originX + size.getX() - 1, originX, originX + size.getX() - 1, originX + size.getX() / 2};
        int[] zs = {originZ, originZ, originZ + size.getZ() - 1, originZ + size.getZ() - 1, originZ + size.getZ() / 2};
        for (int i = 0; i < xs.length; i++) {
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xs[i], zs[i]);

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dy = 0; dy <= WATER_SCAN_DEPTH; dy++) {
                pos.set(xs[i], groundY - dy, zs[i]);
                if (!level.getBlockState(pos).getFluidState().isEmpty()) return false;
            }

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, xs[i], zs[i]);
            if (surfaceY - groundY > MAX_CANOPY_HEIGHT) return false;
        }
        return true;
    }

    /** Максимум по 4 углам footprint'а (грунт без листвы) — меньше риска утопить башню в холме или подвесить в воздухе. */
    private static int computeGroundY(ServerLevel level, int x, int z, Vec3i size) {
        int h1 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int h2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + size.getX() - 1, z);
        int h3 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z + size.getZ() - 1);
        int h4 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + size.getX() - 1, z + size.getZ() - 1);
        return Math.max(Math.max(h1, h2), Math.max(h3, h4));
    }

    /**
     * Заливает ровную площадку под всей башней (с запасом
     * {@link #PLATFORM_MARGIN}) на уровне groundY — верхний слой дёрн
     * ({@code divinerpg:frozen_grass}), под ним земля ({@code
     * divinerpg:frozen_dirt}). Сверху дёрна кладём декоративный слой снега
     * ({@code minecraft:snow} — тонкий слой, не блок). Расчищает всё, что
     * торчит выше площадки (пни, стволы), и отдельно вырубает деревья/листву
     * ещё дальше по краям ({@link #TREE_CLEAR_MARGIN}).
     *
     * <p>{@link #CORE_DIRT_LAYERS} слоёв земли кладутся безусловно (гарантия
     * ровной площадки прямо под башней), а следующие {@link #EXTRA_DIRT_LAYERS}
     * — только там, где сейчас воздух или снег: не хотим прогрызать рельеф
     * (скалу, склон) на 10 блоков вниз, просто подстраховываемся от пустот
     * под нависающими краями площадки.
     */
    private static void levelPlatformAndClearTrees(ServerLevel level, int originX, int originZ, Vec3i size, int groundY) {
        Block frozenGrassBlock = ForgeRegistries.BLOCKS.getValue(FROZEN_GRASS_ID);
        Block frozenDirtBlock = ForgeRegistries.BLOCKS.getValue(FROZEN_DIRT_ID);
        BlockState turf = frozenGrassBlock != null ? frozenGrassBlock.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
        BlockState dirt = frozenDirtBlock != null ? frozenDirtBlock.defaultBlockState() : turf;
        BlockState snowLayer = Blocks.SNOW.defaultBlockState();

        int minX = originX - PLATFORM_MARGIN;
        int maxX = originX + size.getX() - 1 + PLATFORM_MARGIN;
        int minZ = originZ - PLATFORM_MARGIN;
        int maxZ = originZ + size.getZ() - 1 + PLATFORM_MARGIN;

        int clearMinX = minX - TREE_CLEAR_MARGIN;
        int clearMaxX = maxX + TREE_CLEAR_MARGIN;
        int clearMinZ = minZ - TREE_CLEAR_MARGIN;
        int clearMaxZ = maxZ + TREE_CLEAR_MARGIN;

        for (int cx = clearMinX >> 4; cx <= clearMaxX >> 4; cx++) {
            for (int cz = clearMinZ >> 4; cz <= clearMaxZ >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }

        int turfTopY = groundY - 1;
        int coreBottomY = turfTopY - CORE_DIRT_LAYERS; // низ безусловной части (исключительно)
        int extraBottomY = coreBottomY - EXTRA_DIRT_LAYERS; // низ условной части (исключительно)

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = clearMinX; x <= clearMaxX; x++) {
            for (int z = clearMinZ; z <= clearMaxZ; z++) {
                boolean onPlatform = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (onPlatform) {
                    // Дёрн — верхний слой.
                    level.setBlock(pos.set(x, turfTopY, z), turf, 2);
                    // Земля — безусловно (CORE_DIRT_LAYERS слоёв).
                    for (int y = coreBottomY; y < turfTopY; y++) {
                        level.setBlock(pos.set(x, y, z), dirt, 2);
                    }
                    // Земля — ещё ниже, но только по воздуху/снегу.
                    for (int y = extraBottomY; y < coreBottomY; y++) {
                        pos.set(x, y, z);
                        BlockState existing = level.getBlockState(pos);
                        if (existing.isAir() || existing.is(Blocks.SNOW)) {
                            level.setBlock(pos, dirt, 2);
                        }
                    }
                    // Расчистка над площадкой (пни, стволы, всё лишнее).
                    for (int y = groundY; y < groundY + CLEAR_HEIGHT; y++) {
                        pos.set(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                    // Декоративный снежок поверх дёрна — сама башня при
                    // установке шаблона перекроет его в своих пределах.
                    level.setBlock(pos.set(x, groundY, z), snowLayer, 2);
                } else {
                    // За пределами площадки — только деревья/листву, землю не трогаем.
                    for (int y = groundY - 2; y < groundY + CLEAR_HEIGHT; y++) {
                        pos.set(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    private static void placeTemplate(ServerLevel level, StructureTemplate template, BlockPos pos) {
        Vec3i size = template.getSize();
        int minChunkX = pos.getX() >> 4;
        int maxChunkX = (pos.getX() + size.getX()) >> 4;
        int minChunkZ = pos.getZ() >> 4;
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
