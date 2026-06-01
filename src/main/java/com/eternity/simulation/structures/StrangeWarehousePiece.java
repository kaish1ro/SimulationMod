package com.eternity.simulation.structures;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

public class StrangeWarehousePiece extends TemplateStructurePiece {

    private static final ResourceLocation TEMPLATE =
            new ResourceLocation(SimulationMod.MODID, "strange_warehouse");

    private static final ResourceLocation BLUEPRINT =
            new ResourceLocation("simulation", "chests/strange_warehouse_blueprint");
    private static final ResourceLocation COMMON =
            new ResourceLocation("simulation", "chests/strange_warehouse_common");

    public StrangeWarehousePiece(StructureTemplateManager manager, BlockPos origin) {
        super(ModStructures.STRANGE_WAREHOUSE_PIECE.get(), 0, manager,
                TEMPLATE, TEMPLATE.toString(),
                new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE),
                origin);
    }

    public StrangeWarehousePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.STRANGE_WAREHOUSE_PIECE.get(), tag, context.structureTemplateManager(),
                rl -> new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
        assignLoot(level, random);
    }

    // Назначаем лут-таблицы после размещения шаблона: один случайный сундук — blueprint, остальные — common.
    // Каждая половина двойного сундука считается отдельно, поэтому один слот из всего склада получит blueprint.
    private void assignLoot(WorldGenLevel level, RandomSource random) {
        List<RandomizableContainerBlockEntity> chests = new ArrayList<>();

        for (BlockPos bp : BlockPos.betweenClosed(
                boundingBox.minX(), boundingBox.minY(), boundingBox.minZ(),
                boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ())) {
            BlockEntity be = level.getBlockEntity(bp);
            if (be instanceof RandomizableContainerBlockEntity container) {
                chests.add(container);
            }
        }

        if (chests.isEmpty()) return;

        int blueprintIdx = random.nextInt(chests.size());
        for (int i = 0; i < chests.size(); i++) {
            ResourceLocation table = (i == blueprintIdx) ? BLUEPRINT : COMMON;
            chests.get(i).setLootTable(table, random.nextLong());
        }
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
    }
}
