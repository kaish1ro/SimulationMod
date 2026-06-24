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
import java.util.Optional;
import java.util.Set;

/**
 * Батчевое "доделывание" нижних частей башен замка, которые из-за неровного рельефа
 * часто висят в воздухе ниже уровня пола замка (castleBaseY).
 *
 * Обрабатывается весь bounding box замка ({@link CastleFootprint}).
 *
 * Для каждой колонки спускаемся вниз от castleBaseY не глубже, чем на
 * {@link CastleConstants#TOWER_FIX_DEPTH} блоков, и останавливаемся, если встретили
 * силовое поле (кольцо игрока — глубже не лезем). Каждый раз, встречая любой сплошной
 * блок (не важно, какой — это может быть кастомный материал замка), под которым
 * воздух — копируем этот блок вниз, заполняя пустоту, пока не дойдём до сплошного
 * блока (естественная земля) или до силового поля. Затем продолжаем спуск дальше —
 * так обрабатываются все границы по очереди, а не только самая нижняя.
 */
public final class CastleTowerFixTask {

    /** Только блоки этого тега копируем вниз — иначе вниз уезжает deadrock/рельеф (столбы). */
    private static final TagKey<Block> CASTLE_BLOCKS_TAG =
        TagKey.create(Registries.BLOCK, new ResourceLocation("twilightforest", "castle_blocks"));

    private static final String[] FORCE_FIELD_IDS = {
        "twilightforest:blue_force_field",
        "twilightforest:violet_force_field",
        "twilightforest:pink_force_field",
        "twilightforest:orange_force_field",
        "twilightforest:green_force_field",
    };

    /** Шипы — копируемый блок "проезжает" сквозь них вниз, как сквозь воздух: иногда
     *  башня висит над участком, заросшим шипами, а не над пустотой. */
    private static final String[] THORN_IDS = {
        "twilightforest:brown_thorns",
        "twilightforest:green_thorns",
    };

    private static Set<Block> forceFieldBlocks;
    private static Set<Block> thornBlocks;

    private static ArrayDeque<ChunkPos> queue;
    private static ServerLevel activeLevel;
    private static ServerPlayer requester;

    private static int footMinX, footMaxX, footMinZ, footMaxZ;
    private static int topY, yMin;
    private static long filledCount;

    private CastleTowerFixTask() {}

    public static boolean isRunning() {
        return queue != null;
    }

    public static boolean start(ServerLevel level, BlockPos anchor, ServerPlayer requester) {
        if (queue != null) return false;

        Optional<CastleFootprint> fpOpt = CastleFootprint.compute(level, anchor);
        if (fpOpt.isEmpty()) {
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§c[simcastle] §7Не удалось вычислить bounding box замка (не найдена одна из структур)."));
            }
            return false;
        }

        CastleFootprint fp = fpOpt.get();
        footMinX = fp.minX;
        footMaxX = fp.maxX;
        footMinZ = fp.minZ;
        footMaxZ = fp.maxZ;

        int castleBaseY = anchor.getY() + CastleConstants.CASTLE_OFFSET.getY();
        topY = castleBaseY;
        yMin = topY - CastleConstants.TOWER_FIX_DEPTH;

        int cxMin = footMinX >> 4, cxMax = footMaxX >> 4;
        int czMin = footMinZ >> 4, czMax = footMaxZ >> 4;

        queue = new ArrayDeque<>();
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                queue.add(new ChunkPos(cx, cz));
            }
        }

        activeLevel = level;
        filledCount = 0;
        CastleTowerFixTask.requester = requester;

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§e[simcastle] §7Доделывание башен начато: §f" + queue.size() + " чанков§7. Footprint X "
                    + footMinX + ".." + footMaxX + ", Z " + footMinZ + ".." + footMaxZ
                    + "§7, topY=§f" + topY));
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
                    "§a[simcastle] §7Доделывание башен завершено. Поставлено блоков: §f" + filledCount));
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
                processColumn(x, z);
            }
        }
    }

    private static void processColumn(int x, int z) {
        Set<Block> forceFields = getForceFieldBlocks();

        for (int y = topY; y >= yMin; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = activeLevel.getBlockState(pos);
            if (forceFields.contains(state.getBlock())) break;
            if (state.isAir()) continue;

            // Копируем вниз ТОЛЬКО блоки самого замка. Иначе вниз уезжает рельеф
            // (deadrock, который ландшафт ставит на targetY = castleBaseY - 2) — это
            // и давало паразитные столбы под замком.
            if (!state.is(CASTLE_BLOCKS_TAG)) continue;

            BlockPos belowPos = new BlockPos(x, y - 1, z);
            if (!activeLevel.getBlockState(belowPos).isAir()) continue;

            // Глубину здесь не ограничиваем: иногда башня целиком висит в воздухе
            // выше TOWER_FIX_DEPTH — гоним блок вниз, пока не упрёмся в землю
            // (или в шипы, через которые проходим как сквозь воздух).
            Set<Block> thorns = getThornBlocks();
            int worldMinY = activeLevel.getMinBuildHeight();
            for (int fy = y - 1; fy >= worldMinY; fy--) {
                BlockPos fillPos = new BlockPos(x, fy, z);
                BlockState fillTarget = activeLevel.getBlockState(fillPos);
                if (forceFields.contains(fillTarget.getBlock())) break;
                if (!fillTarget.isAir() && !thorns.contains(fillTarget.getBlock())) break;

                activeLevel.setBlock(fillPos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                filledCount++;
            }
        }
    }

    private static Set<Block> getForceFieldBlocks() {
        if (forceFieldBlocks == null) {
            forceFieldBlocks = new HashSet<>();
            for (String id : FORCE_FIELD_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) forceFieldBlocks.add(block);
            }
        }
        return forceFieldBlocks;
    }

    private static Set<Block> getThornBlocks() {
        if (thornBlocks == null) {
            thornBlocks = new HashSet<>();
            for (String id : THORN_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) thornBlocks.add(block);
            }
        }
        return thornBlocks;
    }
}
