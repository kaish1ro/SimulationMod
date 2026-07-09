package com.eternity.simulation.structures;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Древний колодец — самодостаточный шаблон: сундуки с лором уже заполнены прямо
 * в NBT, никаких data-маркеров (villager_spawn/loot_table) в палитре нет, поэтому
 * postProcess/handleDataMarker ничего дополнительно не делают.
 */
public class AncientWellPiece extends TemplateStructurePiece {

    // package-private: используется и в AncientWellStructure для проверки футпринта
    static final ResourceLocation TEMPLATE =
            new ResourceLocation(SimulationMod.MODID, "ancient_well");

    public AncientWellPiece(StructureTemplateManager manager, BlockPos origin) {
        super(ModStructures.ANCIENT_WELL_PIECE.get(), 0, manager,
                TEMPLATE, TEMPLATE.toString(),
                new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE),
                origin);
    }

    public AncientWellPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.ANCIENT_WELL_PIECE.get(), tag, context.structureTemplateManager(),
                rl -> new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE));
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // нет data-маркеров в шаблоне — ничего не делаем
    }
}
