package com.eternity.simulation.structures;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Копия ванильного {@code MapItem.renderBiomePreviewMap} с одним ключевым
 * отличием: биомы берутся напрямую из {@link BiomeSource} генератора (чистый
 * шум, мгновенно), а не через {@code level.getBiome()}, который форсирует
 * генерацию каждого затронутого чанка. Охват карты при scale=2 — 1024x1024
 * блока, то есть до ~4000 чанков: ванильный путь на свежей, нетронутой
 * местности блокировал сервер настолько, что карта приходила клиенту пустой
 * (тот самый «первый крафт всегда пустой»). Шумовой путь даёт тот же рисунок
 * суша/вода — ванильный рендер сам смотрит только на тег биома
 * WATER_ON_MAP_OUTLINES, рельеф ему не нужен.
 */
public final class VoidBlossomMapPreview {

    private VoidBlossomMapPreview() {}

    public static void render(ServerLevel level, ItemStack mapStack) {
        MapItemSavedData data = MapItem.getSavedData(mapStack, level);
        if (data == null) return;

        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();

        int blocksPerPixel = 1 << data.scale;
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        boolean[] water = new boolean[16384];
        int startX = centerX / blocksPerPixel - 64;
        int startZ = centerZ / blocksPerPixel - 64;

        for (int pz = 0; pz < 128; ++pz) {
            for (int px = 0; px < 128; ++px) {
                int blockX = (startX + px) * blocksPerPixel;
                int blockZ = (startZ + pz) * blocksPerPixel;
                Holder<Biome> biome = biomeSource.getNoiseBiome(
                        QuartPos.fromBlock(blockX), QuartPos.fromBlock(0), QuartPos.fromBlock(blockZ), sampler);
                water[pz * 128 + px] = biome.is(BiomeTags.WATER_ON_MAP_OUTLINES);
            }
        }

        // Дальше — ванильная отрисовка контуров без изменений.
        for (int x = 1; x < 127; ++x) {
            for (int z = 1; z < 127; ++z) {
                int wetNeighbours = 0;
                for (int dx = -1; dx < 2; ++dx) {
                    for (int dz = -1; dz < 2; ++dz) {
                        if ((dx != 0 || dz != 0) && isWater(water, x + dx, z + dz)) {
                            ++wetNeighbours;
                        }
                    }
                }

                MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                MapColor color = MapColor.NONE;
                if (isWater(water, x, z)) {
                    color = MapColor.COLOR_ORANGE;
                    if (wetNeighbours > 7 && z % 2 == 0) {
                        switch ((x + (int) (Mth.sin((float) z + 0.0F) * 7.0F)) / 8 % 5) {
                            case 0:
                            case 4:
                                brightness = MapColor.Brightness.LOW;
                                break;
                            case 1:
                            case 3:
                                brightness = MapColor.Brightness.NORMAL;
                                break;
                            case 2:
                                brightness = MapColor.Brightness.HIGH;
                        }
                    } else if (wetNeighbours > 7) {
                        color = MapColor.NONE;
                    } else if (wetNeighbours > 5) {
                        brightness = MapColor.Brightness.NORMAL;
                    } else if (wetNeighbours > 3) {
                        brightness = MapColor.Brightness.LOW;
                    } else if (wetNeighbours > 1) {
                        brightness = MapColor.Brightness.LOW;
                    }
                } else if (wetNeighbours > 0) {
                    color = MapColor.COLOR_BROWN;
                    brightness = wetNeighbours > 3 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOWEST;
                }

                if (color != MapColor.NONE) {
                    data.setColor(x, z, color.getPackedId(brightness));
                }
            }
        }
    }

    private static boolean isWater(boolean[] water, int x, int z) {
        return water[z * 128 + x];
    }
}
