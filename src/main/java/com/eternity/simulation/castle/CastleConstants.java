package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Геометрические константы для перестройки канонического Final Castle
 * относительно castleAnchorPos (позиция стенда "Final Castle WIP.").
 */
public final class CastleConstants {

    private CastleConstants() {}

    public static final ResourceKey<Level> TWILIGHT_FOREST_DIM = ResourceKey.create(
        Registries.DIMENSION,
        new ResourceLocation("twilightforest", "twilight_forest")
    );

    /** Смещение origin'а castle.nbt относительно castleAnchorPos. */
    public static final BlockPos CASTLE_OFFSET = new BlockPos(-56, -66, -67);

    /** Смещение origin'а labyrinth.nbt относительно castleAnchorPos. */
    public static final BlockPos LABYRINTH_OFFSET = new BlockPos(-38, -109, -52);

    /** Смещение origin'а castle_roof.nbt относительно castleAnchorPos (ставится после победы над боссом). */
    public static final BlockPos CASTLE_ROOF_OFFSET = new BlockPos(-18, 0, -18);

    /** Смещение origin'а blue_tower_bottom.nbt относительно castleAnchorPos (низ большой синей башни). */
    public static final BlockPos BLUE_TOWER_BOTTOM_OFFSET = new BlockPos(63, -124, -10);

    /** Допуск по Y вокруг уровня базы, в пределах которого нижний блок силового поля считается "кольцом игрока" (а не декором внутри замка). */
    public static final int FORCE_FIELD_RING_TOLERANCE = 6;

    /** Толщина "оболочки" вокруг периметра bounding box замка, в которой ищем кольцо силового поля. */
    public static final int FORCE_FIELD_RING_SHELL = 4;

    /** Максимальная глубина (от castleBaseY вниз), на которую достраиваем низы башен.
     *  Платформа ландшафта на targetY = castleBaseY - 2, плюс запас 2 блока на случай,
     *  если башня всё равно зависает чуть выше платформы. */
    public static final int TOWER_FIX_DEPTH = 4;

    /** Радиус зачистки старых блоков замка вокруг anchor'а по X/Z. */
    public static final int CLEAR_RADIUS_HORIZONTAL = 200;

    /** На сколько блоков выше anchor'а зачищать. */
    public static final int CLEAR_RADIUS_UP = 70;

    /** На сколько блоков ниже anchor'а зачищать. */
    public static final int CLEAR_RADIUS_DOWN = 170;

    /** Сколько чанков обрабатывать за один тик при батчевой зачистке. */
    public static final int CLEAR_CHUNKS_PER_TICK = 2;

    /** Ниже этого мирового Y при зачистке #twilightforest:castle_blocks с вероятностью
     *  {@link #CASTLE_BLOCK_THORN_REPLACE_CHANCE_NUM}/3 ставим вместо воздуха
     *  brown_thorns/thorn_leaves — заполняет "проплешины" в thornlands снаружи замка.
     *  На подвальные уровни это не влияет, поверх них всё равно встанет NBT-структура. */
    public static final int CASTLE_BLOCK_THORN_REPLACE_MAX_Y = 58;

    /** Вероятность (числитель из 3) замены снесённого castle_block на шипы
     *  ниже {@link #CASTLE_BLOCK_THORN_REPLACE_MAX_Y}. */
    public static final int CASTLE_BLOCK_THORN_REPLACE_CHANCE_NUM = 2;

    /** Вероятность (числитель из 5) того, что при замене снесённого castle_block на шипы
     *  будет выбран thorn_leaves вместо brown_thorns. */
    public static final int CASTLE_BLOCK_THORN_LEAVES_CHANCE_NUM = 1;

    /** Целевая высота "плато" внутри footprint'а замка: весь deadrock/cracked_deadrock
     *  на этой высоте и выше превращается в weathered_deadrock. */
    public static final int TERRAIN_PLATEAU_Y = 95;

    /** Верхняя граница сканирования для прохода, превращающего deadrock/cracked_deadrock
     *  в weathered_deadrock на {@link #TERRAIN_PLATEAU_Y} и выше. */
    public static final int TERRAIN_PLATEAU_SCAN_TOP = 100;

    /** Нижняя граница (исключительно) для поиска "уступов" deadrock/cracked_deadrock
     *  с воздухом сверху — такие столбцы досыпаются weathered_deadrock до {@link #TERRAIN_PLATEAU_Y}. */
    public static final int TERRAIN_LIP_MIN_Y = 80;

    /** Сторона квадратной области (в блоках) вокруг центра footprint'а замка, в которой
     *  {@code CastleTerrainFillTask} ищет и засыпает "ямы" от снесённых башен/частей замка. */
    public static final int TERRAIN_FILL_AREA_SIZE = 250;

    /** Нижняя граница сканирования "природной" поверхности deadrock в {@code CastleTerrainFillTask}
     *  (глубже не ищем — там может быть естественная пустота/пещеры). */
    public static final int TERRAIN_FILL_SCAN_BOTTOM_Y = 40;

    /** Верхняя граница сканирования поверхности в {@code CastleTerrainFillTask}. Берём с запасом
     *  выше плато, чтобы не «упереться в потолок», если рельеф этого замка выше TERRAIN_PLATEAU_Y. */
    public static final int TERRAIN_FILL_SCAN_TOP_Y = 160;

    /** Число итераций релаксации (SOR) при восстановлении карты высот в {@code CastleTerrainFillTask}. */
    public static final int TERRAIN_FILL_SOR_ITERATIONS = 300;

    /** Коэффициент over-relaxation для SOR в {@code CastleTerrainFillTask} (1.0 = обычная диффузия). */
    public static final double TERRAIN_FILL_SOR_OMEGA = 1.6;

    /** Сколько "грязных" столбцов засыпает {@code CastleTerrainFillTask} за один тик. */
    public static final int TERRAIN_FILL_COLUMNS_PER_TICK = 16;
}
