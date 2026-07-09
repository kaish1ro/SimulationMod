package com.eternity.simulation.blocks;

import com.eternity.simulation.ModFluids;
import com.eternity.simulation.structures.FrozenBroodmotherSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Крепкий лёд — визуально и физически как обычный лёд (полупрозрачный, скользкий),
 * неломаемый, как бедрок (см. Properties в ModBlocks). Тает ТОЛЬКО от соседства
 * со сверхгорячей лавой (обычная лава/огонь/свет — не действуют).
 *
 * <p>Проверка идёт и по randomTick (на случай, если лава уже стоит рядом и
 * просто ждём своей очереди), и по neighborChanged (для мгновенной реакции,
 * когда лава реально подтекла/появилась только что) — два пути на один и тот
 * же результат, без дублирования логики.
 */
public class SolidIceBlock extends HalfTransparentBlock {

    public SolidIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isTouchingSuperhotLava(level, pos)) {
            melt(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (level instanceof ServerLevel serverLevel && isTouchingSuperhotLava(serverLevel, pos)) {
            melt(serverLevel, pos);
        }
    }

    private static boolean isTouchingSuperhotLava(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            FluidState fluid = level.getFluidState(pos.relative(dir));
            if (fluid.getType() == ModFluids.SUPERHOT_LAVA_SOURCE.get()
                    || fluid.getType() == ModFluids.SUPERHOT_LAVA_FLOWING.get()) {
                return true;
            }
        }
        return false;
    }

    private static void melt(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        FrozenBroodmotherSpawner.onIceMelted(level, pos);
    }
}
