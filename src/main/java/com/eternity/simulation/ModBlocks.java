package com.eternity.simulation;

import com.eternity.simulation.blocks.DoubleCompressedCobblestone;
import com.eternity.simulation.blocks.QuadroCompressedCobblestone;
import com.eternity.simulation.blocks.SimulationWorkbenchBlock;
import com.eternity.simulation.blocks.TripleCompressedCobblestone;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, SimulationMod.MODID);

    // Предметы блоков регистрируем здесь же для удобства — отдельный регистр.
    public static final DeferredRegister<Item> BLOCK_ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, SimulationMod.MODID);

    public static final RegistryObject<Block> SIMULATION_WORKBENCH = BLOCKS.register(
        "technical_workbench",
        () -> new SimulationWorkbenchBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.5f)
                .sound(SoundType.STONE)
        )
    );

    public static final RegistryObject<Block> DOUBLE_COMPRESSED_COBBLESTONE = BLOCKS.register(
            "double_compressed_cobblestone",
            () -> new DoubleCompressedCobblestone(
                    BlockBehaviour.Properties.copy(Blocks.COBBLESTONE)
            )
    );

    public static final RegistryObject<Block> TRIPLE_COMPRESSED_COBBLESTONE = BLOCKS.register(
            "triple_compressed_cobblestone",
            () -> new TripleCompressedCobblestone(
                    BlockBehaviour.Properties.copy(Blocks.COBBLESTONE)
            )
    );

    public static final RegistryObject<Block> QUADRO_COMPRESSED_COBBLESTONE = BLOCKS.register(
            "quadro_compressed_cobblestone",
            () -> new QuadroCompressedCobblestone(
                    BlockBehaviour.Properties.copy(Blocks.COBBLESTONE)
            )
    );

    public static final RegistryObject<Item> SIMULATION_WORKBENCH_ITEM = BLOCK_ITEMS.register(
        "technical_workbench",
        () -> new BlockItem(SIMULATION_WORKBENCH.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> DOUBLE_COMPRESSED_COBBLESTONE_ITEM = BLOCK_ITEMS.register(
            "double_compressed_cobblestone",
            () -> new BlockItem(DOUBLE_COMPRESSED_COBBLESTONE.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> TRIPLE_COMPRESSED_COBBLESTONE_ITEM = BLOCK_ITEMS.register(
            "triple_compressed_cobblestone",
            () -> new BlockItem(TRIPLE_COMPRESSED_COBBLESTONE.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> QUADRO_COMPRESSED_COBBLESTONE_ITEM = BLOCK_ITEMS.register(
            "quadro_compressed_cobblestone",
            () -> new BlockItem(QUADRO_COMPRESSED_COBBLESTONE.get(), new Item.Properties())
    );
}
