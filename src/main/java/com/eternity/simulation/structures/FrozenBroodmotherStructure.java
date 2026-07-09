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
 * Тот же подход, что у {@link HerbalistsHouseStructure}/{@link AncientWellStructure}
 * (WORLD_SURFACE_WG не работает в Undergarden из-за потолка измерения) — ищем пол
 * через NoiseColumn. Низ глыбы (Y=0 шаблона) уже почти сплошной лёд (округлый
 * сугроб) — сажаем прямо на найденный пол, без смещения внутрь.
 *
 * <p>В отличие от Ancient Well эта структура НАЗЕМНАЯ (сугроб сидит на видимой
 * поверхности frostfields, а не встраивается в породу) — поэтому скан ищет
 * именно верхнюю границу рельефа: самый верхний "воздух над твёрдым" во всём
 * диапазоне высот, а не в узком окне sea level..+48. Раньше окно было обрезано
 * этим диапазоном — если реальная поверхность frostfields оказывалась выше или
 * ниже него, скан почти всегда проваливался, отсюда и крайне редкий спавн. Вода
 * тоже раньше засчитывалась как "твёрдая" (баг) — теперь явно исключена.
 */
public class FrozenBroodmotherStructure extends Structure {

    public static final Codec<FrozenBroodmotherStructure> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    settingsCodec(instance)
            ).apply(instance, FrozenBroodmotherStructure::new));

    private static final int MAX_FLOOR_VARIANCE = 6;
    private static final int REQUIRED_HEADROOM = 4;

    public FrozenBroodmotherStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();

        int minY = context.heightAccessor().getMinBuildHeight();

        Optional<StructureTemplate> template = context.structureTemplateManager().get(FrozenBroodmotherPiece.TEMPLATE);
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
            int standY = findFloorY(context, p[0], p[1]);
            if (standY < 0) return Optional.empty();
            minStandY = Math.min(minStandY, standY);
            maxStandY = Math.max(maxStandY, standY);
        }

        if (maxStandY - minStandY > MAX_FLOOR_VARIANCE) {
            return Optional.empty();
        }

        if (maxStandY < minY) return Optional.empty();

        BlockPos pos = new BlockPos(x, maxStandY, z);
        return Optional.of(new GenerationStub(pos, builder ->
                builder.addPiece(new FrozenBroodmotherPiece(context.structureTemplateManager(), pos))));
    }

    private static int findFloorY(GenerationContext context, int x, int z) {
        int maxY = context.heightAccessor().getMaxBuildHeight();
        int minY = context.heightAccessor().getMinBuildHeight();
        NoiseColumn column = context.chunkGenerator().getBaseColumn(
                x, z, context.heightAccessor(), context.randomState());

        // Полный диапазон высот сверху вниз — реальная поверхность frostfields
        // может оказаться выше или ниже узкого окна sea level..+48.
        for (int y = maxY - 8; y > minY; y--) {
            var block = column.getBlock(y);
            var below = column.getBlock(y - 1);
            boolean airHere = block.isAir();
            boolean solidBelow = !below.isAir() && below.getFluidState().isEmpty(); // вода не твёрдая!
            if (airHere && solidBelow && hasHeadroom(column, y)) {
                return y;
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
        return ModStructures.FROZEN_BROODMOTHER.get();
    }
}
