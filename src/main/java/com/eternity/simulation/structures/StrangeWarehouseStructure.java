package com.eternity.simulation.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

public class StrangeWarehouseStructure extends Structure {

    public static final Codec<StrangeWarehouseStructure> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    settingsCodec(instance)
            ).apply(instance, StrangeWarehouseStructure::new));

    public StrangeWarehouseStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        int surfaceY = context.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int oceanFloorY = context.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());

        // Если поверхность выше дна — между ними вода, пропускаем
        if (surfaceY > oceanFloorY + 1) return Optional.empty();

        BlockPos pos = new BlockPos(x, surfaceY, z);
        return Optional.of(new GenerationStub(pos, builder ->
                builder.addPiece(new StrangeWarehousePiece(context.structureTemplateManager(), pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.STRANGE_WAREHOUSE.get();
    }
}
