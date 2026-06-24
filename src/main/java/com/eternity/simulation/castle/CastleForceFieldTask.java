package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Батчевое продление кольца синего силового поля {@code twilightforest:blue_force_field}
 * (расставленного игроком вокруг периметра замка) вниз — до упора в любой блок
 * семейства deadrock. Всё, что не deadrock (воздух, шипы, листья шипов и т.п.),
 * заменяется на силовое поле.
 *
 * Обрабатывается только "оболочка" толщиной {@link CastleConstants#FORCE_FIELD_RING_SHELL}
 * вокруг периметра bounding box замка ({@link CastleFootprint}), и только колонки, где
 * самый нижний блок силового поля лежит НИЖЕ нижней границы структур замка — это отличает
 * кольцо игрока от декоративных силовых полей внутри самой структуры.
 *
 * Работает в два прохода (batched по {@link CastleConstants#CLEAR_CHUNKS_PER_TICK} чанков
 * за тик):
 * <ul>
 *   <li>{@link Phase#PLACE} — расстановка блоков силового поля вниз (как раньше);</li>
 *   <li>{@link Phase#CONNECT} — пересчёт "соединений" (свойства PipeBlock NORTH/SOUTH/EAST/WEST/UP/DOWN)
 *       для каждого блока силового поля в обработанной области. {@code setBlock} без флага
 *       UPDATE_NEIGHBORS не вызывает {@code updateShape}, поэтому новые блоки остаются с
 *       {@code defaultBlockState()} (все соединения false) — это крошечный 2x2x2 кубик
 *       в центре блока с огромными зазорами по краям ("трещины", через которые пролетает
 *       жемчуг эндера). Проход CONNECT включает соединение в каждую сторону, где соседний
 *       блок — тоже силовое поле или просто "sturdy"-блок (deadrock, наш слой и т.п.),
 *       заполняя зазоры максимально плотно.</li>
 * </ul>
 */
public final class CastleForceFieldTask {

    private static final String[] DEADROCK_IDS = {
        "twilightforest:deadrock",
        "twilightforest:cracked_deadrock",
        "twilightforest:weathered_deadrock",
    };

    private enum Phase { PLACE, CONNECT }

    private static Set<Block> deadrockBlocks;
    private static Block blueForceFieldBlock;

    private static ArrayDeque<ChunkPos> queue;
    private static Phase phase;
    private static ServerLevel activeLevel;
    private static int footMinX, footMaxX, footMinZ, footMaxZ;
    private static int xMin, xMax, yMin, yMax, zMin, zMax;
    private static int ringLevel;
    private static long placedCount;
    private static long connectedCount;
    private static ServerPlayer requester;

    private CastleForceFieldTask() {}

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
        ringLevel = anchor.getY() + CastleConstants.CASTLE_OFFSET.getY();

        int shell = CastleConstants.FORCE_FIELD_RING_SHELL;
        xMin = footMinX - shell;
        xMax = footMaxX + shell;
        zMin = footMinZ - shell;
        zMax = footMaxZ + shell;
        yMin = anchor.getY() - CastleConstants.CLEAR_RADIUS_DOWN;
        yMax = anchor.getY() + CastleConstants.CLEAR_RADIUS_UP;

        queue = buildChunkQueue();
        phase = Phase.PLACE;

        activeLevel = level;
        placedCount = 0;
        connectedCount = 0;
        CastleForceFieldTask.requester = requester;

        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "§e[simcastle] §7Продление силового поля начато: §f" + queue.size() + " чанков§7. "
                    + "Footprint X " + footMinX + ".." + footMaxX + ", Z " + footMinZ + ".." + footMaxZ
                    + "§7, уровень кольца Y=§f" + ringLevel
                    + " §7(±" + CastleConstants.FORCE_FIELD_RING_TOLERANCE + ")"));
        }
        return true;
    }

    private static ArrayDeque<ChunkPos> buildChunkQueue() {
        int cxMin = xMin >> 4, cxMax = xMax >> 4;
        int czMin = zMin >> 4, czMax = zMax >> 4;

        ArrayDeque<ChunkPos> q = new ArrayDeque<>();
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                q.add(new ChunkPos(cx, cz));
            }
        }
        return q;
    }

    public static void tick() {
        if (queue == null) return;

        for (int i = 0; i < CastleConstants.CLEAR_CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            processChunk(queue.poll());
        }

        if (queue.isEmpty()) {
            if (phase == Phase.PLACE) {
                // Переходим ко второму проходу — пересчёт соединений силового поля.
                queue = buildChunkQueue();
                phase = Phase.CONNECT;
                return;
            }

            queue = null;
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(
                    "§a[simcastle] §7Продление силового поля завершено. Поставлено блоков: §f" + placedCount
                        + "§7, пересчитано соединений: §f" + connectedCount));
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

        Block forceField = getBlueForceField();
        if (forceField == null) return;

        int shell = CastleConstants.FORCE_FIELD_RING_SHELL;

        for (int x = chunkXStart; x <= chunkXEnd; x++) {
            for (int z = chunkZStart; z <= chunkZEnd; z++) {
                boolean deepInterior = x > footMinX + shell && x < footMaxX - shell
                    && z > footMinZ + shell && z < footMaxZ - shell;
                if (deepInterior) continue;

                if (phase == Phase.PLACE) {
                    processPlaceColumn(x, z, forceField, forceField.defaultBlockState());
                } else {
                    processConnectColumn(x, z, forceField);
                }
            }
        }
    }

    private static void processPlaceColumn(int x, int z, Block forceField, BlockState forceFieldState) {
        // Самый нижний блок силового поля в колонке (первый снизу).
        int bottomY = Integer.MIN_VALUE;
        for (int y = yMin; y <= yMax; y++) {
            if (activeLevel.getBlockState(new BlockPos(x, y, z)).getBlock() == forceField) {
                bottomY = y;
                break;
            }
        }
        if (bottomY == Integer.MIN_VALUE) return;

        // Это кольцо игрока только если нижний блок рядом с уровнем базы замка.
        // Декоративные поля внутри замка выше базы — пропускаем.
        int tol = CastleConstants.FORCE_FIELD_RING_TOLERANCE;
        if (bottomY < ringLevel - tol || bottomY > ringLevel + tol) return;

        Set<Block> deadrock = getDeadrockBlocks();
        for (int y = bottomY - 1; y >= yMin; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = activeLevel.getBlockState(pos);
            if (deadrock.contains(state.getBlock())) break;

            activeLevel.setBlock(pos, forceFieldState, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            placedCount++;
        }
    }

    /**
     * Пересчитывает соединения (NORTH/SOUTH/EAST/WEST/UP/DOWN) для каждого блока силового
     * поля в колонке: соединение включается, если соседний блок в эту сторону — тоже
     * силовое поле, либо просто "sturdy"-блок (deadrock, наш слой ландшафта и т.п.).
     * Это заполняет зазоры между {@code defaultBlockState()}-блоками, поставленными в
     * фазе PLACE (а также чинит соединение DOWN исходного кольца с продлённой частью).
     */
    private static void processConnectColumn(int x, int z, Block forceField) {
        for (int y = yMin; y <= yMax; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState current = activeLevel.getBlockState(pos);
            if (current.getBlock() != forceField) continue;

            BlockState updated = forceField.defaultBlockState();
            if (current.hasProperty(BlockStateProperties.WATERLOGGED)) {
                updated = updated.setValue(BlockStateProperties.WATERLOGGED, current.getValue(BlockStateProperties.WATERLOGGED));
            }

            for (Direction dir : Direction.values()) {
                BlockPos relPos = pos.relative(dir);
                BlockState relState = activeLevel.getBlockState(relPos);
                boolean connect = relState.getBlock() == forceField
                    || relState.isFaceSturdy(activeLevel, relPos, dir.getOpposite());
                updated = updated.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connect);
            }

            if (!updated.equals(current)) {
                activeLevel.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                connectedCount++;
            }
        }
    }

    private static Block getBlueForceField() {
        if (blueForceFieldBlock == null) {
            blueForceFieldBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("twilightforest", "blue_force_field"));
        }
        return blueForceFieldBlock;
    }

    private static Set<Block> getDeadrockBlocks() {
        if (deadrockBlocks == null) {
            deadrockBlocks = new HashSet<>();
            for (String id : DEADROCK_IDS) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block != null && block != Blocks.AIR) deadrockBlocks.add(block);
            }
        }
        return deadrockBlocks;
    }
}
