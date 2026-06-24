package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Bounding box (X/Z) и нижняя граница (Y) всех NBT-структур замка
 * (castle/labyrinth/castle_roof/blue_tower_bottom) относительно castleAnchorPos.
 *
 * Используется для выравнивания ландшафта ({@link CastleTerrainTask}) и для
 * продления силового поля только вдоль реального периметра замка ({@link CastleForceFieldTask}).
 */
public final class CastleFootprint {

    private static final ResourceLocation[] STRUCTURES = {
        CastleStructures.CASTLE,
        CastleStructures.LABYRINTH,
        CastleStructures.CASTLE_ROOF,
        CastleStructures.BLUE_TOWER_BOTTOM,
    };

    private static final BlockPos[] OFFSETS = {
        CastleConstants.CASTLE_OFFSET,
        CastleConstants.LABYRINTH_OFFSET,
        CastleConstants.CASTLE_ROOF_OFFSET,
        CastleConstants.BLUE_TOWER_BOTTOM_OFFSET,
    };

    public final int minX, maxX, minZ, maxZ, minY;

    private CastleFootprint(int minX, int maxX, int minZ, int maxZ, int minY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.minY = minY;
    }

    public static Optional<CastleFootprint> compute(ServerLevel level, BlockPos anchor) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;

        for (int i = 0; i < STRUCTURES.length; i++) {
            Optional<Vec3i> sizeOpt = CastleStructures.getTemplateSize(level, STRUCTURES[i]);
            if (sizeOpt.isEmpty()) return Optional.empty();

            Vec3i size = sizeOpt.get();
            BlockPos offset = OFFSETS[i];
            int x0 = anchor.getX() + offset.getX();
            int z0 = anchor.getZ() + offset.getZ();
            int y0 = anchor.getY() + offset.getY();
            int x1 = x0 + size.getX() - 1;
            int z1 = z0 + size.getZ() - 1;

            minX = Math.min(minX, x0);
            maxX = Math.max(maxX, x1);
            minZ = Math.min(minZ, z0);
            maxZ = Math.max(maxZ, z1);
            minY = Math.min(minY, y0);
        }

        return Optional.of(new CastleFootprint(minX, maxX, minZ, maxZ, minY));
    }
}
