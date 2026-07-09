package com.eternity.simulation.blocks;

import com.eternity.simulation.ModBlocks;
import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Превращает обычный {@code minecraft:lava_cauldron}, оказавшийся над
 * {@code minecraft:soul_fire}, в {@link SuperhotLavaCauldronBlock} — без этого
 * лава в котле никогда не начнёт нагреваться. Срабатывает в обе стороны: и когда
 * котёл с лавой ставится НА уже горящий огонь души, и когда огонь души
 * разжигается ПОД уже стоящим котлом с лавой.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class SuperhotLavaListener {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        if (state.is(Blocks.SOUL_FIRE)) {
            tryUpgrade(level, pos.above());
        } else if (state.is(Blocks.LAVA_CAULDRON)) {
            tryUpgrade(level, pos);
        }
    }

    private static void tryUpgrade(ServerLevel level, BlockPos cauldronPos) {
        if (!level.getBlockState(cauldronPos).is(Blocks.LAVA_CAULDRON)) return;
        if (!level.getBlockState(cauldronPos.below()).is(Blocks.SOUL_FIRE)) return;

        level.setBlock(cauldronPos, ModBlocks.SUPERHOT_LAVA_CAULDRON.get().defaultBlockState(), 3);
        if (level.getBlockState(cauldronPos).getBlock() instanceof SuperhotLavaCauldronBlock block) {
            block.scheduleHeating(level, cauldronPos);
        }
    }
}
