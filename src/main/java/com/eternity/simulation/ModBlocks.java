package com.eternity.simulation;

import com.eternity.simulation.blocks.CastleKeyDoorBlock;
import com.eternity.simulation.blocks.CastleKeyDoorBlockEntity;
import com.eternity.simulation.blocks.DoubleCompressedCobblestone;
import com.eternity.simulation.blocks.HerbalistsTableBlock;
import com.eternity.simulation.blocks.QuadroCompressedCobblestone;
import com.eternity.simulation.blocks.SimulationWorkbenchBlock;
import com.eternity.simulation.blocks.SolidIceBlock;
import com.eternity.simulation.blocks.SuperhotLavaCauldronBlock;
import com.eternity.simulation.blocks.TripleCompressedCobblestone;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
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

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SimulationMod.MODID);

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

    // ── Дверь замка TF ────────────────────────────────────────────────────────

    /**
     * Дверной блок финального замка Twilight Forest.
     * Открывается только с {@link com.eternity.simulation.ModItems#CASTLE_KEY}.
     * При открытии BFS-волна открывает весь связный проём.
     * Неломаемый в survival (strength = -1), чтобы нельзя было пробить кирками.
     */
    public static final RegistryObject<Block> CASTLE_KEY_DOOR = BLOCKS.register(
            "castle_key_door",
            () -> new CastleKeyDoorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(-1f, 3_600_000f)   // неломаемый в survival
                            .sound(SoundType.DEEPSLATE)
                            .noOcclusion()               // соседние грани всегда рендерятся
                            .requiresCorrectToolForDrops()
            )
    );

    public static final RegistryObject<Item> CASTLE_KEY_DOOR_ITEM = BLOCK_ITEMS.register(
            "castle_key_door",
            () -> new BlockItem(CASTLE_KEY_DOOR.get(), new Item.Properties())
    );

    // ── Стол Травника (блок профессии Strange Herbalist) ─────────────────────

    public static final RegistryObject<Block> HERBALISTS_TABLE = BLOCKS.register(
        "herbalists_table",
        () -> new HerbalistsTableBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops()
                // Кастомная (не полнокубовая) модель: без noOcclusion игра считает блок
                // сплошным и срезает грани соседей → сквозь дыры модели виден x-ray.
                .noOcclusion()
        )
    );

    public static final RegistryObject<Item> HERBALISTS_TABLE_ITEM = BLOCK_ITEMS.register(
        "herbalists_table",
        () -> new BlockItem(HERBALISTS_TABLE.get(), new Item.Properties())
    );

    /** BlockEntityType для хранения door_id каждого блока двери. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CastleKeyDoorBlockEntity>> CASTLE_KEY_DOOR_BE_TYPE =
            BLOCK_ENTITY_TYPES.register("castle_key_door",
                    () -> BlockEntityType.Builder
                            .of(CastleKeyDoorBlockEntity::new, CASTLE_KEY_DOOR.get())
                            .build(null));

    // ── Крепкий лёд — как обычный лёд (полупрозрачный, скользкий), но не тает ──

    public static final RegistryObject<Block> SOLID_ICE = BLOCKS.register(
        "solid_ice",
        () -> new SolidIceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                // Неломаемый, как бедрок — убрать может только сверхгорячая лава (код).
                .strength(-1.0F, 3600000.0F)
                .friction(0.98f)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .randomTicks() // нужно для проверки соседства со сверхгорячей лавой
        )
    );

    public static final RegistryObject<Item> SOLID_ICE_ITEM = BLOCK_ITEMS.register(
        "solid_ice",
        () -> new BlockItem(SOLID_ICE.get(), new Item.Properties())
    );

    // ── Котёл со сверхгорячей лавой ────────────────────────────────────────────
    // Без item-формы — как и ванильный lava_cauldron, появляется только подменой
    // блока в мире (см. SuperhotLavaListener), вручную поставить его нельзя.
    public static final RegistryObject<Block> SUPERHOT_LAVA_CAULDRON = BLOCKS.register(
        "superhot_lava_cauldron",
        () -> new SuperhotLavaCauldronBlock(
            BlockBehaviour.Properties.copy(Blocks.CAULDRON)
                .lightLevel(state -> 15)
        )
    );

    // ── Жидкость сверхгорячей лавы (блок в мире) ────────────────────────────────
    public static final RegistryObject<LiquidBlock> SUPERHOT_LAVA_BLOCK = BLOCKS.register(
        "superhot_lava",
        () -> new LiquidBlock(
            () -> (FlowingFluid) ModFluids.SUPERHOT_LAVA_SOURCE.get(),
            BlockBehaviour.Properties.copy(Blocks.LAVA)
                .lightLevel(state -> 15)
        )
    );
}
