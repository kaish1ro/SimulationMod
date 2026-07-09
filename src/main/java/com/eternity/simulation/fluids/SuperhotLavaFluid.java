package com.eternity.simulation.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.ForgeFlowingFluid;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Сверхгорячая лава — поведение и физика как у обычной лавы (урон, поджигание
 * горючих соседей, реакция с водой → камень+шипение), просто отдельная жидкость
 * со своими текстурами/ведром. Способность плавить крепкий лёд — отдельная задача.
 *
 * <p>Код почти полностью зеркалит ванильный {@code LavaFluid} (см. декомпиляцию),
 * только без зависимости от ultrawarm-измерений (там не нужно — фиксированные
 * значения как в обычном измерении).
 */
public abstract class SuperhotLavaFluid extends ForgeFlowingFluid {

    protected SuperhotLavaFluid(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == getSource() || fluid == getFlowing();
    }

    @Override
    public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir() && !level.getBlockState(above).isSolidRender(level, above)) {
            if (random.nextInt(100) == 0) {
                double x = pos.getX() + random.nextDouble();
                double y = pos.getY() + 1.0;
                double z = pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
                level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                        0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
            if (random.nextInt(200) == 0) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS,
                        0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }
    }

    @Override
    public void randomTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
        int i = random.nextInt(3);
        if (i > 0) {
            BlockPos p = pos;
            for (int j = 0; j < i; j++) {
                p = p.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!level.isLoaded(p)) return;
                BlockState bs = level.getBlockState(p);
                if (bs.isAir()) {
                    if (hasFlammableNeighbours(level, p)) {
                        level.setBlockAndUpdate(p, ForgeEventFactory.fireFluidPlaceBlockEvent(level, p, pos, Blocks.FIRE.defaultBlockState()));
                        return;
                    }
                } else if (bs.blocksMotion()) {
                    return;
                }
            }
        } else {
            for (int k = 0; k < 3; k++) {
                BlockPos p = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!level.isLoaded(p)) return;
                if (level.isEmptyBlock(p.above()) && isFlammable(level, p, Direction.UP)) {
                    level.setBlockAndUpdate(p.above(), ForgeEventFactory.fireFluidPlaceBlockEvent(level, p.above(), pos, Blocks.FIRE.defaultBlockState()));
                }
            }
        }
    }

    private boolean hasFlammableNeighbours(LevelReader level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (isFlammable(level, pos.relative(dir), dir.getOpposite())) return true;
        }
        return false;
    }

    private boolean isFlammable(LevelReader level, BlockPos pos, Direction face) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.hasChunkAt(pos) && level.getBlockState(pos).isFlammable(level, pos, face);
    }

    @Nullable
    @Override
    public ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_LAVA;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        fizz(level, pos);
    }

    private void fizz(LevelAccessor level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }

    @Override
    public int getSlopeFindDistance(LevelReader level) {
        return 2;
    }

    @Override
    public int getDropOff(LevelReader level) {
        return 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return state.getHeight(level, pos) >= 0.44444445F && fluid.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return 30;
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    // Лава + вода = камень и шипение, как в ванили (не завязано на тег fluid/lava,
    // т.к. этот класс по определению представляет только нашу лавоподобную жидкость)
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        if (direction == Direction.DOWN) {
            FluidState existing = level.getFluidState(pos);
            if (existing.is(FluidTags.WATER)) {
                if (state.getBlock() instanceof LiquidBlock) {
                    level.setBlock(pos, ForgeEventFactory.fireFluidPlaceBlockEvent(level, pos, pos, Blocks.STONE.defaultBlockState()), 3);
                }
                fizz(level, pos);
                return;
            }
        }
        super.spreadTo(level, pos, state, direction, fluidState);
    }

    public static class Flowing extends SuperhotLavaFluid {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends SuperhotLavaFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
