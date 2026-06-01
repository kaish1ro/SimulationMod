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
}
