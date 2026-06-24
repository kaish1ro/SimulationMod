package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Загрузка и установка кастомных NBT-структур замка (castle/labyrinth), записанных
 * через structure block в creative-мире под именами {@code simulation:castle} и
 * {@code simulation:labyrinth}.
 */
public final class CastleStructures {

    public static final ResourceLocation CASTLE = new ResourceLocation("simulation", "castle");
    public static final ResourceLocation LABYRINTH = new ResourceLocation("simulation", "labyrinth");
    public static final ResourceLocation CASTLE_ROOF = new ResourceLocation("simulation", "castle_roof");
    public static final ResourceLocation BLUE_TOWER_BOTTOM = new ResourceLocation("simulation", "blue_tower_bottom");

    private CastleStructures() {}

    /**
     * Ставит структуру в мир и собирает все DATA-маркеры внутри её bounding box,
     * заменяя сами маркерные блоки на воздух.
     *
     * @return список маркеров (в мировых координатах), или empty если структура не найдена.
     */
    public static Optional<List<CastleDataMarker>> placeAndCollectMarkers(
            ServerLevel level, ResourceLocation structureId, BlockPos placePos) {

        StructureTemplateManager manager = level.getServer().getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(structureId);
        if (templateOpt.isEmpty()) return Optional.empty();

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();

        template.placeInWorld(level, placePos, placePos, settings, level.getRandom(), Block.UPDATE_CLIENTS);

        Vec3i size = template.getSize(settings.getRotation());
        BlockPos min = placePos;
        BlockPos max = placePos.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        List<CastleDataMarker> markers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof StructureBlockEntity sbe
                    && sbe.getMode() == StructureMode.DATA) {

                markers.add(new CastleDataMarker(pos.immutable(), sbe.getMetaData()));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        return Optional.of(markers);
    }

    /**
     * Возвращает размер (X/Y/Z) сохранённой структуры без её установки в мир —
     * используется для расчёта bounding box замка под выравнивание ландшафта.
     */
    public static Optional<Vec3i> getTemplateSize(ServerLevel level, ResourceLocation structureId) {
        return level.getServer().getStructureManager().get(structureId).map(StructureTemplate::getSize);
    }
}
