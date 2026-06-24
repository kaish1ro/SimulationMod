package com.eternity.simulation.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class HerbalistsHouseStructure extends Structure {

    public static final Codec<HerbalistsHouseStructure> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    settingsCodec(instance)
            ).apply(instance, HerbalistsHouseStructure::new));

    // Внутренний пол дома (mogmoss_rug) лежит на относительной высоте Y=2 шаблона.
    // Сдвигаем шаблон вниз на это значение, чтобы пол совпал с полом пещеры.
    private static final int INTERIOR_FLOOR_OFFSET = 2;

    public HerbalistsHouseStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        int seaLevel = context.chunkGenerator().getSeaLevel(); // Undergarden = 32
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();

        // Стартуем из середины пещерного слоя и сканируем ВНИЗ до уровня моря —
        // так находим открытый пол пещеры, а не потолок и не глубокий карман.
        NoiseColumn column = context.chunkGenerator().getBaseColumn(
                x, z, context.heightAccessor(), context.randomState());

        int startY = Math.min(maxY - 8, seaLevel + 48);
        int standY = -1;
        for (int y = startY; y > seaLevel; y--) {
            boolean airHere = column.getBlock(y).isAir();
            boolean solidBelow = !column.getBlock(y - 1).isAir();
            if (airHere && solidBelow) {
                standY = y; // первый «воздух над твёрдым» = поверхность пола
                break;
            }
        }

        // Не нашли валидный пол выше уровня моря — пропускаем чанк
        if (standY < 0 || standY - INTERIOR_FLOOR_OFFSET < minY) {
            return Optional.empty();
        }

        BlockPos pos = new BlockPos(x, standY - INTERIOR_FLOOR_OFFSET, z);
        return Optional.of(new GenerationStub(pos, builder ->
                builder.addPiece(new HerbalistsHousePiece(context.structureTemplateManager(), pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HERBALISTS_HOUSE.get();
    }
}
