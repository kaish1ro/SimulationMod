package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Батчевое косметическое выравнивание ландшафта внутри footprint'а замка.
 *
 * Тривиальный двухпроходный алгоритм по столбцам (x,z) footprint'а:
 *  1) весь {@code deadrock}/{@code cracked_deadrock} на высоте {@link CastleConstants#TERRAIN_PLATEAU_Y}
 *     и выше (до {@link CastleConstants#TERRAIN_PLATEAU_SCAN_TOP}) превращается в {@code weathered_deadrock}
 *     — получаем плоское "плато" на TERRAIN_PLATEAU_Y.
 *  2) если в столбце на высоте строго между {@link CastleConstants#TERRAIN_LIP_MIN_Y} и
 *     TERRAIN_PLATEAU_Y нашёлся deadrock/cracked_deadrock с воздухом сверху (открытый "уступ"),
 *     досыпаем столбец weathered_deadrock от этого уступа до TERRAIN_PLATEAU_Y включительно.
 *
 * Каждый столбец обрабатывается за один проход (без сходимости). Результат может быть не
 * идеально гладким на сложном рельефе — это чисто косметическая правка, не влияющая на геймплей.
 */
public final class CastleTerrainTask {

    // Дно/стена для прохода выравнивания — weathered_deadrock сюда НЕ входит (это уже "плато").
    private static final String[] DEADROCK_IDS = {
        "twilightforest:deadrock",
        "twilightforest:cracked_deadrock",
    };

    private static Block weatheredDeadrockBlock;
    private static Set<Block> deadrockBlocks;

    private static ArrayDeque<ChunkPos> queue;
    private static ServerLevel activeLevel;
    private static ServerPlayer requester;

    private static int footMinX, footMaxX, footMinZ, footMaxZ;
    private static long changedCount;

    private CastleTerrainTask() {}

    public static boolean isRunning() {
        return queue != null;
    }

    public static boolean start(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        if (queue != null) return false;

        Optional<CastleFootprint> footprintOpt = CastleFootprint.compute(level, anchor);
        if (footprintOpt.isEmpty()) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§c[simcastle] §7Не удалось определить footprint замка (шаблоны не загружены)."));
            }
            return false;
        }

        CastleFootprint footprint = footprintOpt.get();
        footMinX = footprint.minX;
        footMaxX = footprint.maxX;
        footMinZ = footprint.minZ;
        footMaxZ = footprint.maxZ;

        activeLevel = level;
        CastleTerrainTask.requester = requester;
        changedCount = 0;
        queue = buildFootprintChunkQueue();

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§e[simcastle] §7Выравнивание ландшафта начато: §f" + queue.size() + " чанков§7, плато на Y="
                    + CastleConstants.TERRAIN_PLATEAU_Y + "."));
        }
        return true;
    }

    private static ArrayDeque<ChunkPos> buildFootprintChunkQueue() {
        ArrayDeque<ChunkPos> result = new ArrayDeque<>();
        int cxMin = footMinX >> 4;
        int cxMax = footMaxX >> 4;
        int czMin = footMinZ >> 4;
        int czMax = footMaxZ >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                result.add(new ChunkPos(cx, cz));
            }
        }
        return result;
    }

    public static void tick() {
        if (queue == null) return;

        for (int i = 0; i < CastleConstants.CLEAR_CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            processChunk(queue.poll());
        }

        if (queue.isEmpty()) {
            queue = null;
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§a[simcastle] §7Выравнивание ландшафта завершено. Поставлено блоков: §f" + changedCount));
            }
            activeLevel = null;
            requester = null;
        }
    }

    private static void processChunk(ChunkPos chunk) {
        int xStart = Math.max(footMinX, chunk.getMinBlockX());
        int xEnd = Math.min(footMaxX, chunk.getMaxBlockX());
        int zStart = Math.max(footMinZ, chunk.getMinBlockZ());
        int zEnd = Math.min(footMaxZ, chunk.getMaxBlockZ());

        for (int x = xStart; x <= xEnd; x++) {
            for (int z = zStart; z <= zEnd; z++) {
                levelColumn(x, z);
            }
        }
    }

    private static void levelColumn(int x, int z) {
        Block weathered = getWeatheredDeadrock();
        Set<Block> trigger = getDeadrockBlocks();
        if (weathered == null) return;

        // Проход 1: deadrock/cracked_deadrock на Y >= TERRAIN_PLATEAU_Y -> weathered_deadrock.
        for (int y = CastleConstants.TERRAIN_PLATEAU_SCAN_TOP; y >= CastleConstants.TERRAIN_PLATEAU_Y; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState cur = activeLevel.getBlockState(pos);
            if (trigger.contains(cur.getBlock())) {
                activeLevel.setBlock(pos, weathered.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                changedCount++;
            }
        }

        // Проход 2: уступ deadrock/cracked_deadrock с воздухом сверху, выше TERRAIN_LIP_MIN_Y
        // и ниже TERRAIN_PLATEAU_Y -> досыпаем weathered_deadrock до TERRAIN_PLATEAU_Y.
        for (int y = CastleConstants.TERRAIN_PLATEAU_Y - 1; y > CastleConstants.TERRAIN_LIP_MIN_Y; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState cur = activeLevel.getBlockState(pos);
            if (!trigger.contains(cur.getBlock())) continue;

            BlockPos above = pos.above();
            if (!activeLevel.getBlockState(above).isAir()) continue;

            for (int fy = y + 1; fy <= CastleConstants.TERRAIN_PLATEAU_Y; fy++) {
                BlockPos fillPos = new BlockPos(x, fy, z);
                if (!activeLevel.getBlockState(fillPos).isAir()) break;
                activeLevel.setBlock(fillPos, weathered.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                changedCount++;
            }
            break;
        }
    }

    private static Set<Block> getDeadrockBlocks() {
        if (deadrockBlocks == null) {
            deadrockBlocks = new HashSet<>();
            for (String id : DEADROCK_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null) deadrockBlocks.add(block);
            }
        }
        return deadrockBlocks;
    }

    private static Block getWeatheredDeadrock() {
        if (weatheredDeadrockBlock == null) {
            weatheredDeadrockBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("twilightforest", "weathered_deadrock"));
        }
        return weatheredDeadrockBlock;
    }
}
