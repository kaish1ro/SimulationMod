package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Засыпка "ям" в ландшафте — мест, где снесённые башни/части замка ({@link CastleClearTask})
 * обнажили провал в природном deadrock-рельефе.
 *
 * <p>Отдельная команда {@code /simcastle terrainfill}, альтернатива плоскому плато
 * {@link CastleTerrainTask} (не заменяет её):
 * <ol>
 *   <li>строим карту высот {@link CastleConstants#TERRAIN_FILL_AREA_SIZE}×{@link CastleConstants#TERRAIN_FILL_AREA_SIZE}
 *       вокруг центра footprint'а замка — для каждого столбца ищем верхний deadrock/cracked_deadrock/weathered_deadrock;</li>
 *   <li>природная поверхность (склоны, плато) сверху всегда выветрена — её верхний блок
 *       {@code weathered_deadrock}. Провалы от снесённых башен обнажают "свежий" камень —
 *       {@code deadrock}/{@code cracked_deadrock}. Поэтому столбец считается "ямой" (грязным),
 *       только если его верхний блок — {@code deadrock} или {@code cracked_deadrock};
 *       остальные столбцы — границы мембраны;</li>
 *   <li>грязные клетки восстанавливаются релаксацией (SOR) к среднему соседей —
 *       получается гладкая "мембрана", наследующая уклон границ;</li>
 *   <li>в грязных столбцах досыпаем воздух {@code weathered_deadrock} от
 *       {@link CastleConstants#TERRAIN_FILL_SCAN_BOTTOM_Y} до восстановленной высоты —
 *       естественные пещеры и уже поставленные NBT-структуры не трогаются (только воздух).</li>
 * </ol>
 */
public final class CastleTerrainFillTask {

    private static final String[] GROUND_BLOCK_IDS = {
        "twilightforest:deadrock",
        "twilightforest:cracked_deadrock",
        "twilightforest:weathered_deadrock",
    };

    /** "Свежий" (невыветренный) deadrock — верхний блок такого типа выдаёт провал
     *  от снесённой башни/части замка, а не природный склон/плато (там сверху weathered_deadrock). */
    private static final String[] RAW_DEADROCK_BLOCK_IDS = {
        "twilightforest:deadrock",
        "twilightforest:cracked_deadrock",
    };

    /** Шипы, заросшие в провалах (например, после поломки склонов баганными башнями) —
     *  при засыпке ломаются и заменяются, а не блокируют заливку. */
    private static final String[] THORN_BLOCK_IDS = {
        "twilightforest:brown_thorns",
        "twilightforest:green_thorns",
        "twilightforest:thorn_leaves",
    };

    private static Set<Block> groundBlocks;
    private static Set<Block> rawDeadrockBlocks;
    private static Set<Block> thornBlocks;
    private static Block weatheredDeadrockBlock;

    private static ArrayDeque<int[]> dirtyColumns;
    private static double[][] height;
    private static int originX, originZ;
    private static ServerLevel activeLevel;
    private static ServerPlayer requester;
    private static long filledCount;

    private CastleTerrainFillTask() {}

    public static boolean isRunning() {
        return dirtyColumns != null;
    }

    public static boolean start(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        if (dirtyColumns != null) return false;

        Optional<CastleFootprint> footprintOpt = CastleFootprint.compute(level, anchor);
        if (footprintOpt.isEmpty()) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§c[simcastle] §7Не удалось определить footprint замка (шаблоны не загружены)."));
            }
            return false;
        }

        CastleFootprint fp = footprintOpt.get();
        int centerX = (fp.minX + fp.maxX) / 2;
        int centerZ = (fp.minZ + fp.maxZ) / 2;

        int size = CastleConstants.TERRAIN_FILL_AREA_SIZE;
        originX = centerX - size / 2;
        originZ = centerZ - size / 2;

        height = new double[size][size];
        boolean[][] unknown = new boolean[size][size]; // релаксируется (не фиксированная граница)
        boolean[][] fillable = new boolean[size][size]; // реальная яма: попадёт в заливку

        // Проход 1: сырая карта высот. surfaceY == -1 — поверхность не найдена (сквозной провал/застройка).
        int[][] surface = new int[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                surface[i][j] = naturalSurfaceY(level, originX + i, originZ + j);
            }
        }

        // Классификация яма/склон — по типу верхнего блока, а не по форме рельефа: природная
        // поверхность (склоны, плато) всегда выветрена сверху (weathered_deadrock), а провал
        // от снесённой башни обнажает "свежий" deadrock/cracked_deadrock. Классификация
        // выполняется один раз, до начала засыпки — последующая постановка weathered_deadrock
        // в ямы не может "снять" с них пометку fillable.
        //
        // s == -1 (нутро NBT-постройки замка / сквозной войд) НЕ заливаем (чтобы не замуровать замок),
        // но и границей не делаем — иначе его высота утянула бы мембрану вниз.
        double cleanSum = 0;
        int cleanCnt = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int s = surface[i][j];
                if (s < 0) {
                    unknown[i][j] = true; // релаксируем, но не заливаем
                } else if (isRawDeadrock(level.getBlockState(new BlockPos(originX + i, s, originZ + j)))) {
                    unknown[i][j] = true;
                    fillable[i][j] = true; // настоящая яма
                } else {
                    height[i][j] = s; // фиксированная граница мембраны
                    cleanSum += s;
                    cleanCnt++;
                }
            }
        }

        // Стартовое приближение для грязных клеток — средняя высота чистых (нужно только для скорости сходимости).
        double seed = cleanCnt > 0 ? cleanSum / cleanCnt : CastleConstants.TERRAIN_PLATEAU_Y;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (unknown[i][j]) height[i][j] = seed;
            }
        }

        // Диффузия (Гаусс-Зейдель + over-relaxation): грязные клетки тянутся к среднему
        // соседей, чистые клетки — фиксированная граница мембраны.
        double omega = CastleConstants.TERRAIN_FILL_SOR_OMEGA;
        for (int iter = 0; iter < CastleConstants.TERRAIN_FILL_SOR_ITERATIONS; iter++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (!unknown[i][j]) continue;

                    double sum = 0;
                    int n = 0;
                    if (i > 0)        { sum += height[i - 1][j]; n++; }
                    if (i < size - 1) { sum += height[i + 1][j]; n++; }
                    if (j > 0)        { sum += height[i][j - 1]; n++; }
                    if (j < size - 1) { sum += height[i][j + 1]; n++; }

                    double avg = sum / n;
                    height[i][j] += omega * (avg - height[i][j]);
                }
            }
        }

        dirtyColumns = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (fillable[i][j]) dirtyColumns.add(new int[] {i, j});
            }
        }

        if (dirtyColumns.isEmpty()) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal("§e[simcastle] §7Засыпка ям: провалов не найдено."));
            }
            dirtyColumns = null;
            height = null;
            return true;
        }

        activeLevel = level;
        CastleTerrainFillTask.requester = requester;
        filledCount = 0;

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§e[simcastle] §7Засыпка ям начата: §f" + dirtyColumns.size() + " столбцов§7."));
        }
        return true;
    }

    public static void tick() {
        if (dirtyColumns == null) return;

        for (int n = 0; n < CastleConstants.TERRAIN_FILL_COLUMNS_PER_TICK && !dirtyColumns.isEmpty(); n++) {
            fillColumn(dirtyColumns.poll());
        }

        if (dirtyColumns.isEmpty()) {
            dirtyColumns = null;
            height = null;
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§a[simcastle] §7Засыпка ям завершена. Поставлено блоков: §f" + filledCount));
            }
            activeLevel = null;
            requester = null;
        }
    }

    /**
     * Засыпает открытую яму столбца сверху вниз: от восстановленной высоты {@code target}
     * до пола (первого твёрдого блока), ставя {@code weathered_deadrock} в воздух.
     *
     * <p>Защита от замуровывания: если над {@code target} есть хоть один твёрдый блок
     * (крыша/перекрытие замка), столбец считается закрытым и пропускается — заливаем
     * только открытые к небу ямы.
     */
    private static void fillColumn(int[] ij) {
        Block weathered = getWeatheredDeadrock();
        if (weathered == null) return;

        int i = ij[0], j = ij[1];
        int x = originX + i;
        int z = originZ + j;
        int target = (int) Math.round(height[i][j]);

        // Открыта ли яма к небу: над target не должно быть твёрдых блоков (шипы не считаются преградой).
        for (int y = target + 1; y <= CastleConstants.TERRAIN_FILL_SCAN_TOP_Y; y++) {
            BlockState state = activeLevel.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir() && !isThorn(state)) return;
        }

        // Заливаем воздух сверху вниз до пола (первого твёрдого не-шипового блока).
        // Шипы, заросшие в провале, ломаем и заменяем — иначе они "въедаются" в новую насыпь.
        for (int y = target; y >= CastleConstants.TERRAIN_FILL_SCAN_BOTTOM_Y; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = activeLevel.getBlockState(pos);
            if (!state.isAir() && !isThorn(state)) break;

            activeLevel.setBlock(pos, weathered.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            filledCount++;
        }
    }

    /** @return Y верхнего природного deadrock-блока в столбце (x,z), или -1 если не найден. */
    private static int naturalSurfaceY(ServerLevel level, int x, int z) {
        Set<Block> ground = getGroundBlocks();
        for (int y = CastleConstants.TERRAIN_FILL_SCAN_TOP_Y; y >= CastleConstants.TERRAIN_FILL_SCAN_BOTTOM_Y; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (ground.contains(state.getBlock())) return y;
        }
        return -1;
    }

    private static Set<Block> getGroundBlocks() {
        if (groundBlocks == null) {
            groundBlocks = new HashSet<>();
            for (String id : GROUND_BLOCK_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) groundBlocks.add(block);
            }
        }
        return groundBlocks;
    }

    /** @return true, если верхний блок столбца — "свежий" (невыветренный) deadrock/cracked_deadrock,
     *  то есть провал, а не природный склон/плато (там сверху weathered_deadrock). */
    private static boolean isRawDeadrock(BlockState state) {
        if (rawDeadrockBlocks == null) {
            rawDeadrockBlocks = new HashSet<>();
            for (String id : RAW_DEADROCK_BLOCK_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) rawDeadrockBlocks.add(block);
            }
        }
        return rawDeadrockBlocks.contains(state.getBlock());
    }

    private static boolean isThorn(BlockState state) {
        if (thornBlocks == null) {
            thornBlocks = new HashSet<>();
            for (String id : THORN_BLOCK_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) thornBlocks.add(block);
            }
        }
        return thornBlocks.contains(state.getBlock());
    }

    private static Block getWeatheredDeadrock() {
        if (weatheredDeadrockBlock == null) {
            weatheredDeadrockBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("twilightforest", "weathered_deadrock"));
        }
        return weatheredDeadrockBlock;
    }
}
