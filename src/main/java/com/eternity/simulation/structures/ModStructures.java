package com.eternity.simulation.structures;

import com.eternity.simulation.SimulationMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, SimulationMod.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SimulationMod.MODID);

    public static final RegistryObject<StructureType<StrangeWarehouseStructure>> STRANGE_WAREHOUSE =
            STRUCTURE_TYPES.register("strange_warehouse",
                    () -> () -> StrangeWarehouseStructure.CODEC);

    public static final RegistryObject<StructurePieceType> STRANGE_WAREHOUSE_PIECE =
            STRUCTURE_PIECE_TYPES.register("strange_warehouse_piece",
                    () -> StrangeWarehousePiece::new);

    public static final RegistryObject<StructureType<HerbalistsHouseStructure>> HERBALISTS_HOUSE =
            STRUCTURE_TYPES.register("herbalists_house",
                    () -> () -> HerbalistsHouseStructure.CODEC);

    public static final RegistryObject<StructurePieceType> HERBALISTS_HOUSE_PIECE =
            STRUCTURE_PIECE_TYPES.register("herbalists_house_piece",
                    () -> HerbalistsHousePiece::new);

    public static final RegistryObject<StructureType<AncientWellStructure>> ANCIENT_WELL =
            STRUCTURE_TYPES.register("ancient_well",
                    () -> () -> AncientWellStructure.CODEC);

    public static final RegistryObject<StructurePieceType> ANCIENT_WELL_PIECE =
            STRUCTURE_PIECE_TYPES.register("ancient_well_piece",
                    () -> AncientWellPiece::new);

    public static final RegistryObject<StructureType<FrozenBroodmotherStructure>> FROZEN_BROODMOTHER =
            STRUCTURE_TYPES.register("frozen_broodmother",
                    () -> () -> FrozenBroodmotherStructure.CODEC);

    public static final RegistryObject<StructurePieceType> FROZEN_BROODMOTHER_PIECE =
            STRUCTURE_PIECE_TYPES.register("frozen_broodmother_piece",
                    () -> FrozenBroodmotherPiece::new);

    // Цветок пустоты НЕ регистрируется как обычная worldgen-структура — она не должна
    // встречаться при обычном исследовании мира. Появляется только по требованию,
    // в момент крафта карты (см. VoidBlossomMapCraftListener/VoidBlossomSpawner).
}
