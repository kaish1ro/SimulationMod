package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Разовая зачистка "родных" блоков случайно сгенерированного Final Castle
 * вокруг castleAnchorPos перед установкой кастомных NBT-структур.
 *
 * Снимает: #twilightforest:castle_blocks, кварцевые ступени, резной кварц,
 * дубовый забор и таблички, шипы (коричневые/зелёные) и силовые поля всех цветов
 * (включая синее).
 * Обрабатывает по {@link CastleConstants#CLEAR_CHUNKS_PER_TICK} чанков за тик,
 * чтобы не подвесить сервер на большом объёме.
 */
public final class CastleClearTask {

    private static final TagKey<Block> CASTLE_BLOCKS_TAG =
        TagKey.create(Registries.BLOCK, new ResourceLocation("twilightforest", "castle_blocks"));

    private static final String[] EXTRA_BLOCK_IDS = {
        "minecraft:quartz_stairs",
        "minecraft:smooth_quartz_stairs",
        "minecraft:chiseled_quartz_block",
        "minecraft:oak_fence",
        "minecraft:oak_sign",
        "minecraft:oak_wall_sign",
        "twilightforest:violet_force_field",
        "twilightforest:pink_force_field",
        "twilightforest:orange_force_field",
        "twilightforest:green_force_field",
        "twilightforest:blue_force_field",
    };

    /** Шипы — отдельно, т.к. ниже {@link #thornMinY} их не трогаем (см. {@link #start}). */
    private static final String[] THORN_BLOCK_IDS = {
        "twilightforest:brown_thorns",
        "twilightforest:green_thorns",
        "twilightforest:thorn_leaves",
    };

    /** Насколько ниже anchor'а ещё можно чистить шипы. */
    private static final int THORN_CLEAR_MIN_OFFSET = -80;

    private static Set<Block> extraBlocks;
    private static Set<Block> thornBlocks;
    private static Block brownThornsBlock;
    private static boolean brownThornsResolved;
    private static Block thornLeavesBlock;
    private static boolean thornLeavesResolved;

    private static ArrayDeque<ChunkPos> queue;
    private static ServerLevel activeLevel;
    private static int xMin, xMax, yMin, yMax, zMin, zMax;
    private static int thornMinY;
    private static long clearedCount;
    private static ServerPlayer requester;

    private CastleClearTask() {}

    public static boolean isRunning() {
        return queue != null;
    }

    public static boolean start(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        if (queue != null) return false;

        xMin = anchor.getX() - CastleConstants.CLEAR_RADIUS_HORIZONTAL;
        xMax = anchor.getX() + CastleConstants.CLEAR_RADIUS_HORIZONTAL;
        zMin = anchor.getZ() - CastleConstants.CLEAR_RADIUS_HORIZONTAL;
        zMax = anchor.getZ() + CastleConstants.CLEAR_RADIUS_HORIZONTAL;
        yMin = anchor.getY() - CastleConstants.CLEAR_RADIUS_DOWN;
        yMax = anchor.getY() + CastleConstants.CLEAR_RADIUS_UP;
        thornMinY = anchor.getY() + THORN_CLEAR_MIN_OFFSET;

        int cxMin = xMin >> 4;
        int cxMax = xMax >> 4;
        int czMin = zMin >> 4;
        int czMax = zMax >> 4;

        queue = new ArrayDeque<>();
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                queue.add(new ChunkPos(cx, cz));
            }
        }

        activeLevel = level;
        clearedCount = 0;
        CastleClearTask.requester = requester;

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§e[simcastle] §7Зачистка начата: §f" + queue.size() + " чанков§7, Y от §f"
                    + yMin + "§7 до §f" + yMax + "§7."));
        }
        return true;
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
                    "§a[simcastle] §7Зачистка завершена. Снесено блоков: §f" + clearedCount));
            }
            activeLevel = null;
            requester = null;
        }
    }

    private static void processChunk(ChunkPos chunk) {
        int chunkXStart = Math.max(xMin, chunk.getMinBlockX());
        int chunkXEnd = Math.min(xMax, chunk.getMaxBlockX());
        int chunkZStart = Math.max(zMin, chunk.getMinBlockZ());
        int chunkZEnd = Math.min(zMax, chunk.getMaxBlockZ());

        for (int x = chunkXStart; x <= chunkXEnd; x++) {
            for (int z = chunkZStart; z <= chunkZEnd; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = activeLevel.getBlockState(pos);
                    if (shouldClear(state, y)) {
                        activeLevel.setBlock(pos, replacementFor(state, y),
                            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        clearedCount++;
                    }
                }
            }
        }

        // Таблички и прочие вещи, уже лежавшие на земле (не блок-дропы — те и так гасятся
        // UPDATE_SUPPRESS_DROPS выше) — иначе валяются в зоне зачистки и иногда даже
        // ухитряются активировать нажимные плиты (было замечено в синей башне).
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            chunkXStart, yMin, chunkZStart, chunkXEnd + 1, yMax + 1, chunkZEnd + 1);
        for (net.minecraft.world.entity.item.ItemEntity item :
                activeLevel.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            item.discard();
        }
    }

    /**
     * Чем заменить снесённый блок: ниже {@link CastleConstants#CASTLE_BLOCK_THORN_REPLACE_MAX_Y}
     * снесённый castle_block с вероятностью {@link CastleConstants#CASTLE_BLOCK_THORN_REPLACE_CHANCE_NUM}/3
     * заменяется на шипы (заполняет проплешины в thornlands), иначе — воздух. Среди шипов
     * с вероятностью {@link CastleConstants#CASTLE_BLOCK_THORN_LEAVES_CHANCE_NUM}/5 выбирается
     * thorn_leaves, иначе — brown_thorns.
     */
    private static BlockState replacementFor(BlockState state, int y) {
        if (y < CastleConstants.CASTLE_BLOCK_THORN_REPLACE_MAX_Y && state.is(CASTLE_BLOCKS_TAG)) {
            if (activeLevel.getRandom().nextInt(3) < CastleConstants.CASTLE_BLOCK_THORN_REPLACE_CHANCE_NUM) {
                if (activeLevel.getRandom().nextInt(5) < CastleConstants.CASTLE_BLOCK_THORN_LEAVES_CHANCE_NUM) {
                    Block thornLeaves = getThornLeaves();
                    if (thornLeaves != null) return thornLeaves.defaultBlockState();
                }
                Block brownThorns = getBrownThorns();
                if (brownThorns != null) return brownThorns.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static boolean shouldClear(BlockState state, int y) {
        if (state.isAir()) return false;
        if (state.is(CASTLE_BLOCKS_TAG)) return true;
        if (getExtraBlocks().contains(state.getBlock())) return true;
        return y >= thornMinY && getThornBlocks().contains(state.getBlock());
    }

    private static Set<Block> getExtraBlocks() {
        if (extraBlocks == null) {
            extraBlocks = resolveBlocks(EXTRA_BLOCK_IDS);
        }
        return extraBlocks;
    }

    private static Block getBrownThorns() {
        if (!brownThornsResolved) {
            brownThornsBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("twilightforest:brown_thorns"));
            if (brownThornsBlock == Blocks.AIR) brownThornsBlock = null;
            brownThornsResolved = true;
        }
        return brownThornsBlock;
    }

    private static Block getThornLeaves() {
        if (!thornLeavesResolved) {
            thornLeavesBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("twilightforest:thorn_leaves"));
            if (thornLeavesBlock == Blocks.AIR) thornLeavesBlock = null;
            thornLeavesResolved = true;
        }
        return thornLeavesBlock;
    }

    private static Set<Block> getThornBlocks() {
        if (thornBlocks == null) {
            thornBlocks = resolveBlocks(THORN_BLOCK_IDS);
        }
        return thornBlocks;
    }

    private static Set<Block> resolveBlocks(String[] ids) {
        Set<Block> blocks = new HashSet<>();
        for (String id : ids) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block != null && block != Blocks.AIR) {
                blocks.add(block);
            }
        }
        return blocks;
    }
}
