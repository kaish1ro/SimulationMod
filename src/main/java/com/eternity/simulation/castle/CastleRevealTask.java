package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Разовая "реакция" на решение головоломки с пьедесталами синей башни:
 * выполняет группу маркеров {@code remove_statue}, {@code remove_block_below},
 * {@code fill:from=...;to=...;material=...} мгновенно, а {@code ladder} —
 * каскадом сверху вниз с небольшой задержкой между блоками.
 */
public final class CastleRevealTask {

    private static final int LADDER_DELAY_TICKS = 4;

    private static ServerLevel activeLevel;
    private static List<ArrayDeque<BlockPos>> ladderQueues;
    private static int delayCounter;

    private CastleRevealTask() {}

    public static void start(ServerLevel level, List<CastleDataMarker> markers) {
        for (CastleDataMarker marker : markers) {
            if (marker.has("remove_statue")) {
                removeStatue(level, marker.pos());
            }
            if (marker.has("remove_block_below")) {
                level.setBlock(marker.pos().below(), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
            if (marker.has("fill")) {
                applyFill(level, marker);
            }
        }

        List<ArrayDeque<BlockPos>> queues = new ArrayList<>();
        for (CastleDataMarker marker : markers) {
            if (marker.has("ladder")) {
                BlockPos markerPos = marker.pos();
                // Блок над маркером перекрывает верх лестницы — убираем его сразу.
                level.setBlock(markerPos.above(), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                queues.add(buildLadderQueue(markerPos));
            }
        }

        if (!queues.isEmpty()) {
            activeLevel = level;
            ladderQueues = queues;
            delayCounter = 0;
        }
    }

    public static void tick() {
        if (ladderQueues == null) return;
        if (++delayCounter < LADDER_DELAY_TICKS) return;
        delayCounter = 0;

        BlockState ladder = getLadderState();
        if (ladder == null) {
            ladderQueues = null;
            activeLevel = null;
            return;
        }

        Iterator<ArrayDeque<BlockPos>> it = ladderQueues.iterator();
        while (it.hasNext()) {
            ArrayDeque<BlockPos> queue = it.next();
            BlockPos pos = queue.poll();
            if (pos == null) {
                it.remove();
                continue;
            }
            if (activeLevel.getBlockState(pos).isAir()) {
                activeLevel.setBlock(pos, ladder, Block.UPDATE_CLIENTS);
            }
        }

        if (ladderQueues.isEmpty()) {
            ladderQueues = null;
            activeLevel = null;
        }
    }

    /**
     * Убирает статую: 3 блока вниз от маркера превращаются в воздух.
     *
     * <p>Статуи божеств Cataclysm дропают предметы при разрушении даже с флагом
     * {@code UPDATE_SUPPRESS_DROPS} — подчищаем выпавшие итемы сразу же.
     */
    private static void removeStatue(ServerLevel level, BlockPos markerPos) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos pos = markerPos.below(dy);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);

            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0))) {
                drop.discard();
            }
        }
    }

    /**
     * {@code fill:from=X,Y,Z;to=X,Y,Z;material=<block_id>} — координаты относительные
     * к позиции маркера. Используется, например, для "удаления" механизма из командных блоков.
     */
    private static void applyFill(ServerLevel level, CastleDataMarker marker) {
        String fillValue = marker.get("fill");
        String toValue = marker.get("to");
        String materialId = marker.get("material");
        if (fillValue == null || toValue == null || materialId == null) return;

        String fromCoords = fillValue.startsWith("from=") ? fillValue.substring("from=".length()) : fillValue;
        int[] from = parseCoords(fromCoords);
        int[] to = parseCoords(toValue);
        if (from == null || to == null) return;

        Block material = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(materialId));
        if (material == null) return;
        BlockState materialState = material.defaultBlockState();

        BlockPos pos1 = marker.pos().offset(from[0], from[1], from[2]);
        BlockPos pos2 = marker.pos().offset(to[0], to[1], to[2]);
        for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
            level.setBlock(pos.immutable(), materialState, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private static int[] parseCoords(String s) {
        String[] parts = s.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[] {
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** От блока на 1 выше маркера до Y маркера - 6, включительно (8 блоков). */
    private static ArrayDeque<BlockPos> buildLadderQueue(BlockPos markerPos) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (int y = markerPos.getY() + 1; y >= markerPos.getY() - 6; y--) {
            queue.add(new BlockPos(markerPos.getX(), y, markerPos.getZ()));
        }
        return queue;
    }

    private static BlockState getLadderState() {
        Block ladderBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("quark", "ancient_ladder"));
        if (ladderBlock == null) return null;

        BlockState state = ladderBlock.defaultBlockState();
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp && dirProp.getPossibleValues().contains(Direction.SOUTH)) {
                state = state.setValue(dirProp, Direction.SOUTH);
            }
        }
        return state;
    }
}
