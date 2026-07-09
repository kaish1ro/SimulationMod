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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Глыба крепкого льда (23×26×22) с вмёрзшей Ice Brood Mother посередине, на
 * высоте «верх минус 20» (локально X=11, Y=6, Z=11 — см. размер NBT). Сама
 * сущность НЕ хранится в шаблоне (если поместить её туда напрямую — она
 * бесконечно спавнит ice_weaver на любой hurt(), даже будучи Invulnerable;
 * см. {@code IceBroodMotherHurtMixin}) — спавним кодом, отдельно от блоков.
 */
public class FrozenBroodmotherPiece extends TemplateStructurePiece {

    // package-private: используется и в FrozenBroodmotherStructure для проверки футпринта
    static final ResourceLocation TEMPLATE =
            new ResourceLocation(SimulationMod.MODID, "frozen_broodmother");

    // Локальные координаты центра (X/Z = середина 23×22, Y = верх(26) минус 20)
    private static final int SPAWN_LOCAL_X = 11;
    private static final int SPAWN_LOCAL_Y = 6;
    private static final int SPAWN_LOCAL_Z = 11;

    public FrozenBroodmotherPiece(StructureTemplateManager manager, BlockPos origin) {
        super(ModStructures.FROZEN_BROODMOTHER_PIECE.get(), 0, manager,
                TEMPLATE, TEMPLATE.toString(),
                new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE),
                origin);
    }

    public FrozenBroodmotherPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.FROZEN_BROODMOTHER_PIECE.get(), tag, context.structureTemplateManager(),
                rl -> new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);

        // postProcess зовётся один раз НА КАЖДЫЙ чанк, пересекающий структуру (она 23×26×22,
        // легко перекрывает несколько чанков) — без этой проверки боссиха заспавнится
        // несколько раз. Спавним только когда box (текущий обрабатываемый чанк) содержит точку.
        BoundingBox bb = this.getBoundingBox();
        BlockPos spawnPos = new BlockPos(bb.minX() + SPAWN_LOCAL_X, bb.minY() + SPAWN_LOCAL_Y, bb.minZ() + SPAWN_LOCAL_Z);
        if (!box.isInside(spawnPos)) return;

        FrozenBroodmotherSpawner.spawnFrozen(level, spawnPos);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // нет data-маркеров в шаблоне
    }
}
