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

/**
 * {@code getBaseColumn} (единственный доступ к рельефу в
 * {@code findGenerationPoint}) отдаёт ТОЛЬКО шумовой рельеф — без карверных
 * пещер, фич и аквиферных озёр. Ниже уровня моря там сплошная порода и вода,
 * никакой "воздушной полости" искать бессмысленно — её там попросту нет (все
 * пещеры вырезаны карверами уже после, в готовом сейве). Раньше здесь
 * пытались искать такую полость (скан на "воздух сверху + твёрдое снизу") —
 * это почти никогда не находилось в валидном диапазоне и утыкалось в самый
 * верх скана (около потолка измерения), из-за чего колодец вечно всплывал
 * метров на 10 над настоящей землёй; плюс "твёрдое снизу" не отличало воду от
 * камня, поэтому иногда колодец оказывался в воде.
 *
 * <p>Правильный подход (см. память reference_getbasecolumn_no_carvers): не
 * искать полость, а ВСТРАИВАТЬ в породу. Сканируем вниз от уровня моря до
 * первого настоящего твёрдого блока (не воздух и не жидкость — вода не
 * твёрдая!), оголовок ставим сразу над ним, тело уходит в камень. Карверы
 * потом часто вскрывают такую структуру сбоку — колодец "оказывается в
 * пещерах" естественным образом.
 */
public class AncientWellStructure extends Structure {

    public static final Codec<AncientWellStructure> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    settingsCodec(instance)
            ).apply(instance, AncientWellStructure::new));

    // Уровень земли — локальный Y=8 шаблона (перепроверено напрямую в NBT:
    // undergarden:deepturf_block лежит именно на Y=8, сундуки уже стоят на Y=9
    // как на полу поверх дёрна). Раньше здесь стояло 9 — из-за этого офсета
    // на 1 блок весь колодец садился на блок ниже настоящей поверхности: дёрн
    // оказывался под землёй, а место могло затопить, если рядом было озеро.
    private static final int GROUND_LEVEL_OFFSET = 8;

    // Допустимый разброс высоты пола по футпринту — больше значит обрыв/яма, пропускаем.
    private static final int MAX_FLOOR_VARIANCE = 6;

    public AncientWellStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        int seaLevel = context.chunkGenerator().getSeaLevel();
        int minY = context.heightAccessor().getMinBuildHeight();

        Optional<StructureTemplate> template = context.structureTemplateManager().get(AncientWellPiece.TEMPLATE);
        if (template.isEmpty()) return Optional.empty();
        Vec3i size = template.get().getSize();
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;

        int[][] samplePoints = {
                {x, z}, {x - halfX, z - halfZ}, {x + halfX, z - halfZ},
                {x - halfX, z + halfZ}, {x + halfX, z + halfZ}
        };

        int minStandY = Integer.MAX_VALUE;
        int maxStandY = Integer.MIN_VALUE;
        for (int[] p : samplePoints) {
            int standY = findFloorY(context, p[0], p[1], seaLevel, minY);
            if (standY < 0) return Optional.empty();
            minStandY = Math.min(minStandY, standY);
            maxStandY = Math.max(maxStandY, standY);
        }

        if (maxStandY - minStandY > MAX_FLOOR_VARIANCE) {
            return Optional.empty();
        }

        if (maxStandY - GROUND_LEVEL_OFFSET < minY) {
            return Optional.empty();
        }

        BlockPos pos = new BlockPos(x, maxStandY - GROUND_LEVEL_OFFSET, z);
        return Optional.of(new GenerationStub(pos, builder ->
                builder.addPiece(new AncientWellPiece(context.structureTemplateManager(), pos))));
    }

    /** Скан вниз от уровня моря до первого настоящего твёрдого блока (не воздух, не жидкость) — пол сразу над ним. */
    private static int findFloorY(GenerationContext context, int x, int z, int seaLevel, int minY) {
        NoiseColumn column = context.chunkGenerator().getBaseColumn(
                x, z, context.heightAccessor(), context.randomState());

        for (int y = seaLevel; y > minY; y--) {
            var state = column.getBlock(y);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return y + 1;
            }
        }
        return -1;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ANCIENT_WELL.get();
    }
}
