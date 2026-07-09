package com.eternity.simulation.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public class HerbalistsHouseStructure extends Structure {

    public static final Codec<HerbalistsHouseStructure> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    settingsCodec(instance)
            ).apply(instance, HerbalistsHouseStructure::new));

    // Внутренний пол дома (mogmoss_rug) лежит на относительной высоте Y=2 шаблона.
    // Сдвигаем шаблон вниз на это значение, чтобы пол совпал с полом пещеры.
    private static final int INTERIOR_FLOOR_OFFSET = 2;

    // Допустимый разброс высоты пола между точками футпринта. Больше — значит
    // под домом настоящий обрыв/глубокая яма; такой рельеф пропускаем, иначе
    // фундамент-подсыпка превратилась бы в нелепо высокую колонну.
    private static final int MAX_FLOOR_VARIANCE = 6;

    // Сколько блоков воздуха должно быть над полом, чтобы это была реальная
    // пещерная полость/открытое место, а не тонкая щель между слоями шума.
    private static final int REQUIRED_HEADROOM = 4;

    public HerbalistsHouseStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        int seaLevel = context.chunkGenerator().getSeaLevel(); // Undergarden = 32
        int minY = context.heightAccessor().getMinBuildHeight();

        Optional<StructureTemplate> template = context.structureTemplateManager().get(HerbalistsHousePiece.TEMPLATE);
        if (template.isEmpty()) return Optional.empty();
        Vec3i size = template.get().getSize();
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;

        // Раньше пол искали только в центре чанка — на неровном рельефе (обрыв, яма)
        // дом проваливался в грунт с одного края или зависал в воздухе с другого.
        // Теперь проверяем пол по всем 4 углам футпринта дома + центр.
        int[][] samplePoints = {
                {x, z}, {x - halfX, z - halfZ}, {x + halfX, z - halfZ},
                {x - halfX, z + halfZ}, {x + halfX, z + halfZ}
        };

        int minStandY = Integer.MAX_VALUE;
        int maxStandY = Integer.MIN_VALUE;
        for (int[] p : samplePoints) {
            int standY = findFloorY(context, p[0], p[1], seaLevel);
            if (standY < 0) return Optional.empty(); // хоть одна точка без пола/без зазора — пропускаем чанк
            minStandY = Math.min(minStandY, standY);
            maxStandY = Math.max(maxStandY, standY);
        }

        // Разброс высот по футпринту слишком большой — рельеф непригоден (обрыв/яма)
        if (maxStandY - minStandY > MAX_FLOOR_VARIANCE) {
            return Optional.empty();
        }

        // Ставим дом по САМОЙ ВЫСОКОЙ точке пола из выборки. Тогда он гарантированно
        // не въедается в возвышение (нет погребённых стен), а более низкие углы
        // получают подсыпку фундамента вниз до грунта (см. HerbalistsHousePiece.postProcess) —
        // вместо пустого рва вокруг дома будет аккуратный фундамент.
        if (maxStandY - INTERIOR_FLOOR_OFFSET < minY) {
            return Optional.empty();
        }

        BlockPos pos = new BlockPos(x, maxStandY - INTERIOR_FLOOR_OFFSET, z);
        return Optional.of(new GenerationStub(pos, builder ->
                builder.addPiece(new HerbalistsHousePiece(context.structureTemplateManager(), pos))));
    }

    // Стартуем из середины пещерного слоя и сканируем ВНИЗ до уровня моря —
    // так находим открытый пол пещеры, а не потолок и не глубокий карман.
    // Требуем REQUIRED_HEADROOM блоков воздуха над полом, иначе это щель.
    private static int findFloorY(GenerationContext context, int x, int z, int seaLevel) {
        int maxY = context.heightAccessor().getMaxBuildHeight();
        NoiseColumn column = context.chunkGenerator().getBaseColumn(
                x, z, context.heightAccessor(), context.randomState());

        int startY = Math.min(maxY - 8, seaLevel + 48);
        for (int y = startY; y > seaLevel; y--) {
            boolean airHere = column.getBlock(y).isAir();
            boolean solidBelow = !column.getBlock(y - 1).isAir();
            if (airHere && solidBelow && hasHeadroom(column, y)) {
                return y; // первый «воздух над твёрдым» c запасом по высоте = пол
            }
        }
        return -1;
    }

    private static boolean hasHeadroom(NoiseColumn column, int floorY) {
        for (int dy = 0; dy < REQUIRED_HEADROOM; dy++) {
            if (!column.getBlock(floorY + dy).isAir()) return false;
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HERBALISTS_HOUSE.get();
    }
}
