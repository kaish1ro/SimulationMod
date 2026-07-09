package com.eternity.simulation.blocks;

import com.eternity.simulation.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.Map;

/**
 * Котёл с лавой, нагреваемый огнём душ. Появляется не через регистрацию item'а,
 * а подменой обычного {@code minecraft:lava_cauldron}, когда под ним обнаруживается
 * {@code minecraft:soul_fire} (см. {@code SuperhotLavaListener}).
 *
 * <p>{@code STAGE} 0..{@link #MAX_STAGE} — дискретные стадии нагрева (используются
 * и моделью для смены текстуры лавы с оранжевой на голубую, и логикой готовности).
 * На {@code MAX_STAGE} лава становится «сверхгорячей»: шипение + дым, и пустое
 * ведро при наборе даёт {@code superhot_lava_bucket} вместо обычного ведра лавы.
 * До этого момента — по ощущениям обычная лава (можно зачерпнуть как есть).
 *
 * <p>Если огонь души под котлом погас/убран — нагрев останавливается, блок
 * откатывается к ванильному {@code lava_cauldron}.
 */
public class SuperhotLavaCauldronBlock extends AbstractCauldronBlock {

    public static final int MAX_STAGE = 4;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);

    // 60 секунд нагрева / 4 перехода между стадиями = 15 секунд на стадию
    private static final int TICKS_PER_STAGE = 15 * 20;

    public SuperhotLavaCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties, buildInteractions());
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAGE);
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return 0.9375D; // как у LavaCauldronBlock
    }

    @Override
    public boolean isFull(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return 3;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (isEntityInsideContent(state, pos, entity)) {
            entity.lavaHurt();
        }
    }

    /** Планирует первый тик нагрева. Зовётся при создании блока (см. SuperhotLavaListener). */
    public void scheduleHeating(ServerLevel level, BlockPos pos) {
        level.scheduleTick(pos, this, TICKS_PER_STAGE);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Огонь души погас или убран — нагрев прекращается, откат к обычному lava_cauldron
        if (!level.getBlockState(pos.below()).is(Blocks.SOUL_FIRE)) {
            level.setBlock(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
            return;
        }

        int stage = state.getValue(STAGE);
        if (stage >= MAX_STAGE) return; // уже готово

        int next = stage + 1;
        level.setBlock(pos, state.setValue(STAGE, next), 2);

        if (next >= MAX_STAGE) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.2F);
            spawnHeatedSmoke(level, pos, random);
        } else {
            level.scheduleTick(pos, this, TICKS_PER_STAGE);
        }
    }

    private static void spawnHeatedSmoke(ServerLevel level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; i++) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            double y = pos.getY() + 1.0 + random.nextDouble() * 0.3;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.9;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.0, 0.02, 0.0, 0.01);
        }
    }

    // ── Взаимодействие с ведром ─────────────────────────────────────────────

    private static Map<Item, CauldronInteraction> buildInteractions() {
        // ВАЖНО: AbstractCauldronBlock.use() делает interactions.get(item).interact(...)
        // БЕЗ проверки на null — обычный HashMap здесь крашит игру при ПКМ любым предметом,
        // не зарегистрированным в карте явно. Ванильные карты (CauldronInteraction.LAVA и
        // т.п.) построены через newInteractionMap(), которая ставит defaultReturnValue на
        // no-op (PASS) для fastutil-карты — повторяем тот же приём.
        Map<Item, CauldronInteraction> map = CauldronInteraction.newInteractionMap();
        CauldronInteraction.addDefaultInteractions(map); // долив обычной лавы/воды/снега поверх
        map.put(Items.BUCKET, (state, level, pos, player, hand, stack) -> {
            if (!level.isClientSide) {
                boolean ready = state.getValue(STAGE) >= MAX_STAGE;
                ItemStack result = ready
                        ? new ItemStack(ModItems.SUPERHOT_LAVA_BUCKET.get())
                        : new ItemStack(Items.LAVA_BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
                player.awardStat(Stats.FILL_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                level.playSound(null, pos,
                        ready ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BUCKET_EMPTY_LAVA,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        });
        return map;
    }
}
