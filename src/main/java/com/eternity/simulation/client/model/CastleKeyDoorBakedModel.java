package com.eternity.simulation.client.model;

import com.eternity.simulation.blocks.CastleKeyDoorBlock;
import com.eternity.simulation.blocks.CastleKeyDoorBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Запечённая динамическая модель двери замка.
 *
 * <p>В {@link #getModelData} проверяет всех 6 соседей × 4 квадранта
 * каждой грани — учитывая {@code door_id} BlockEntity и состояние {@code open}.
 *
 * <p>В {@link #getQuads} выдаёт для запрошенной грани:
 * <ul>
 *   <li>4 базовых квада (текстура {@code base})</li>
 *   <li>4 оверлейных квада (текстура {@code overlay} или {@code overlay_connected}
 *       в зависимости от {@link ConnectionLogic})</li>
 * </ul>
 */
public class CastleKeyDoorBakedModel implements IDynamicBakedModel {

    /**
     * Ключ для передачи CTM-данных через {@link ModelData}.
     * Хранит массив [6 граней][4 квадранта].
     */
    public static final ModelProperty<ConnectionLogic[][]> DOOR_DATA = new ModelProperty<>();

    @Nullable private final List<BakedQuad>[] baseQuads;
    private final BakedQuad[][][] overlayQuads;
    private final TextureAtlasSprite particle;
    private final ItemOverrides overrides;
    private final ItemTransforms transforms;

    @Nullable private final ChunkRenderTypeSet blockRenderTypes;
    @Nullable private final List<RenderType> itemRenderTypes;
    @Nullable private final List<RenderType> fabulousItemRenderTypes;

    public CastleKeyDoorBakedModel(
            @Nullable List<BakedQuad>[] baseQuads,
            BakedQuad[][][] overlayQuads,
            TextureAtlasSprite particle,
            ItemOverrides overrides,
            ItemTransforms transforms,
            RenderTypeGroup renderTypeGroup
    ) {
        this.baseQuads    = baseQuads;
        this.overlayQuads = overlayQuads;
        this.particle     = particle;
        this.overrides    = overrides;
        this.transforms   = transforms;

        this.blockRenderTypes      = renderTypeGroup.isEmpty() ? null : ChunkRenderTypeSet.of(renderTypeGroup.block());
        this.itemRenderTypes       = renderTypeGroup.isEmpty() ? null : List.of(renderTypeGroup.entity());
        this.fabulousItemRenderTypes = renderTypeGroup.isEmpty() ? null : List.of(renderTypeGroup.entityFabulous());
    }

    // ── ModelData (сервер-сайд чанк, клиентский контекст) ────────────────────

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter getter,
                                           @NotNull BlockPos pos,
                                           @NotNull BlockState state,
                                           @NotNull ModelData existing) {
        ConnectionLogic[][] logic = new ConnectionLogic[6][4];
        String myId = getDoorId(getter, pos);

        for (Direction face : Direction.values()) {
            Direction[] plane = ConnectionLogic.AXIS_PLANE_DIRECTIONS[face.getAxis().ordinal()];
            boolean[] connected = new boolean[4];

            for (int i = 0; i < 4; i++) {
                connected[i] = isSameDoor(getter, pos, plane[i], myId);
            }

            int faceIdx = face.get3DDataValue();
            for (int dir = 0; dir < 4; dir++) {
                int next   = (dir + 1) % 4;
                boolean s1 = connected[dir];
                boolean s2 = connected[next];
                boolean corner = s1 && s2 && isSameCorner(getter, pos, plane[dir], plane[next], myId);
                // Чётный квадрант: первый параметр = dir-сосед, нечётный — swap
                logic[faceIdx][dir] = (dir % 2 == 0)
                        ? ConnectionLogic.of(s1, s2, corner)
                        : ConnectionLogic.of(s2, s1, corner);
            }
        }

        return existing.derive().with(DOOR_DATA, logic).build();
    }

    // ── BakedQuad ─────────────────────────────────────────────────────────────

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state,
                                              @Nullable Direction side,
                                              @NotNull RandomSource random,
                                              @NotNull ModelData extraData,
                                              @Nullable RenderType renderType) {
        if (side == null) return List.of(); // Внутренние квады не нужны

        int faceIdx = side.get3DDataValue();
        ConnectionLogic[][] data = extraData.get(DOOR_DATA);

        // Открытая или анимирующаяся дверь: показываем только светящийся оверлей, базу скрываем
        boolean isOpen = state != null
                && state.getBlock() instanceof CastleKeyDoorBlock
                && (state.getValue(CastleKeyDoorBlock.OPEN) || state.getValue(CastleKeyDoorBlock.VANISHING));

        ArrayList<BakedQuad> result = new ArrayList<>(8);

        // Базовый слой — только для закрытой двери
        if (!isOpen && this.baseQuads != null) {
            result.addAll(this.baseQuads[faceIdx]);
        }

        // Оверлейный слой — всегда (открыто = видна только рамка)
        for (int quad = 0; quad < 4; quad++) {
            ConnectionLogic cl = (data != null) ? data[faceIdx][quad] : ConnectionLogic.NONE;
            result.add(this.overlayQuads[faceIdx][quad][cl.ordinal()]);
        }

        return result;
    }

    // ── Вспомогательные проверки соседей ─────────────────────────────────────

    /**
     * Проверяет, является ли сосед дверью с тем же door_id.
     * Состояние open не проверяется — открытые двери тоже видимы и должны соединяться.
     */
    private static boolean isSameDoor(BlockAndTintGetter getter, BlockPos pos,
                                      Direction dir, String myId) {
        BlockPos nb = pos.relative(dir);
        BlockState nbState = getter.getBlockState(nb);
        if (!(nbState.getBlock() instanceof CastleKeyDoorBlock)) return false;
        return myId.equals(getDoorId(getter, nb));
    }

    /** Проверяет диагональный блок в двух направлениях (угловой сосед). */
    private static boolean isSameCorner(BlockAndTintGetter getter, BlockPos pos,
                                        Direction dir1, Direction dir2, String myId) {
        BlockPos nb = pos.relative(dir1).relative(dir2);
        BlockState nbState = getter.getBlockState(nb);
        if (!(nbState.getBlock() instanceof CastleKeyDoorBlock)) return false;
        return myId.equals(getDoorId(getter, nb));
    }

    /** Возвращает door_id из BlockEntity или DEFAULT_ID при его отсутствии. */
    private static String getDoorId(BlockAndTintGetter getter, BlockPos pos) {
        if (getter.getBlockEntity(pos) instanceof CastleKeyDoorBlockEntity be) {
            return be.getDoorId();
        }
        return CastleKeyDoorBlockEntity.DEFAULT_ID;
    }

    // ── Стандартные методы IBakedModel ────────────────────────────────────────

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d()             { return true; }
    @Override public boolean usesBlockLight()      { return true; }
    @Override public boolean isCustomRenderer()    { return false; }

    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return particle; }
    @Override public @NotNull ItemOverrides      getOverrides()    { return overrides; }
    @Override public @NotNull ItemTransforms     getTransforms()   { return transforms; }

    // ── RenderType ────────────────────────────────────────────────────────────

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state,
                                                       @NotNull RandomSource rand,
                                                       @NotNull ModelData data) {
        return blockRenderTypes != null
                ? blockRenderTypes
                : IDynamicBakedModel.super.getRenderTypes(state, rand, data);
    }

    @Override
    public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
        if (!fabulous && itemRenderTypes != null)         return itemRenderTypes;
        if (fabulous  && fabulousItemRenderTypes != null) return fabulousItemRenderTypes;
        return IDynamicBakedModel.super.getRenderTypes(stack, fabulous);
    }
}
