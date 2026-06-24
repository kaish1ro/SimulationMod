package com.eternity.simulation.client.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Логика CTM-соединения для одного квадранта грани блока.
 *
 * <p>Каждая грань делится на 4 квадранта (8×8 пикселей).
 * Для каждого квадранта проверяются два соседних направления
 * в плоскости грани, а также диагональный угол.
 *
 * <p>Атлас {@code overlay_connected} (16×16) разбит на 4 квадранта:
 * <pre>
 *  CORNERLESS | VERTICAL
 * ------------+----------
 *  HORIZONTAL | CORNER
 * </pre>
 */
public enum ConnectionLogic {

    /** Нет соседей — берём полную текстуру из {@code overlay}. */
    NONE      (0,  0,  0, 16, 16),
    /** Оба соседа + диагональ — угол полностью скрыт. */
    CORNERLESS(1,  0,  0,  8,  8),
    /** Только вертикальный сосед. */
    VERTICAL  (1,  0,  8,  8, 16),
    /** Только горизонтальный сосед. */
    HORIZONTAL(1,  8,  0, 16,  8),
    /** Оба соседа, диагональ отсутствует — угол виден. */
    CORNER    (1,  8,  8, 16, 16);

    private final int textureIdx;
    private final int u0, v0, u1, v1;

    /**
     * Наборы направлений в плоскости каждой оси (X / Y / Z).
     * Используется для обхода 4 квадрантов грани.
     */
    public static final Direction[][] AXIS_PLANE_DIRECTIONS = {
        { Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH }, // X axis
        { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }, // Y axis
        { Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST }    // Z axis
    };

    ConnectionLogic(int textureIdx, int u0, int v0, int u1, int v1) {
        this.textureIdx = textureIdx;
        this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
    }

    /**
     * Определяет тип соединения для квадранта.
     *
     * @param side1  есть ли сосед в первом направлении
     * @param side2  есть ли сосед во втором направлении
     * @param corner есть ли блок в диагональном направлении
     */
    public static ConnectionLogic of(boolean side1, boolean side2, boolean corner) {
        if (corner)  return CORNERLESS;
        if (side1)   return side2 ? CORNER : HORIZONTAL;
        return side2 ? VERTICAL : NONE;
    }

    /** Выбирает текстуру из массива {@code [overlay, overlay_connected]}. */
    public TextureAtlasSprite chooseTexture(TextureAtlasSprite[] sprites) {
        return sprites[this.textureIdx];
    }

    /** Переводит UV-координаты грани в UV-координаты нужного квадранта атласа. */
    public float[] remapUVs(float[] uvs) {
        return new float[]{ getU(uvs[0]), getV(uvs[1]), getU(uvs[2]), getV(uvs[3]) };
    }

    private float getU(float delta) { return this.u0 + (float)(this.u1 - this.u0) * (delta / 16f); }
    private float getV(float delta) { return this.v0 + (float)(this.v1 - this.v0) * (delta / 16f); }
}
