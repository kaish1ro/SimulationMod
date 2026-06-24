package com.eternity.simulation.client.model;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.ForgeFaceData;
import net.minecraftforge.client.model.SimpleModelState;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Vector3f;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;

/**
 * Незапечённая геометрия двери замка.
 *
 * <p>При bake() создаёт для каждой из 6 граней × 4 квадрантов:
 * <ul>
 *   <li>один базовый {@link BakedQuad} (текстура {@code base})</li>
 *   <li>пять оверлейных {@link BakedQuad} — по одному для каждого
 *       состояния {@link ConnectionLogic}</li>
 * </ul>
 * Итого: 6×4×(1+5) = 144 запечённых квада, но в рантайме на каждую
 * грань выводится только 4+4 = 8 (базовый + один оверлей per квадрант).
 */
public class CastleKeyDoorGeometry implements IUnbakedGeometry<CastleKeyDoorGeometry> {

    /** Базовые элементы: [face_3d_value][quad 0..3]. */
    private final BlockElement[][] baseElements;
    /** Оверлейные элементы: [face][quad][ConnectionLogic.ordinal()]. */
    private final BlockElement[][][] faceElements;

    public CastleKeyDoorGeometry() {
        this.baseElements  = new BlockElement[6][4];
        this.faceElements  = new BlockElement[6][4][ConnectionLogic.values().length];

        Vec3i center = new Vec3i(8, 8, 8);

        for (Direction face : Direction.values()) {
            Direction[] plane = ConnectionLogic.AXIS_PLANE_DIRECTIONS[face.getAxis().ordinal()];

            for (int quad = 0; quad < 4; quad++) {
                // Вычисляем угловую точку 8×8×8 квадранта
                Vec3i corner = face.getNormal()
                        .offset(plane[quad].getNormal())
                        .offset(plane[(quad + 1) % 4].getNormal())
                        .offset(1, 1, 1)
                        .multiply(8);

                // Шаблон без граней — нужен только для uvsByFace()
                BlockElement template = new BlockElement(
                        toVec3f(min(center, corner)),
                        toVec3f(max(center, corner)),
                        Map.of(), null, true
                );

                int faceIdx = face.get3DDataValue();

                // Базовый слой (tintIndex=-1 → без окраски)
                this.baseElements[faceIdx][quad] = new BlockElement(
                        template.from, template.to,
                        Map.of(face, new BlockElementFace(face, -1, "",
                                new BlockFaceUV(ConnectionLogic.NONE.remapUVs(template.uvsByFace(face)), 0),
                                null)),
                        null, true
                );

                // Оверлейный слой для каждого состояния соединения
                for (ConnectionLogic logic : ConnectionLogic.values()) {
                    this.faceElements[faceIdx][quad][logic.ordinal()] = new BlockElement(
                            template.from, template.to,
                            Map.of(face, new BlockElementFace(face, 0, "",
                                    new BlockFaceUV(logic.remapUVs(template.uvsByFace(face)), 0),
                                    // Эмиссивный слой: не зависит от освещения, полный блок
                                    new ForgeFaceData(0xFFFFFFFF, 15, 15, true))),
                            null, true
                    );
                }
            }
        }
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context,
                           ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState,
                           ItemOverrides overrides,
                           ResourceLocation modelLocation) {

        // Учитываем корневую трансформацию (display settings)
        Transformation rootTransform = context.getRootTransform();
        if (!rootTransform.isIdentity()) {
            modelState = new SimpleModelState(
                    modelState.getRotation().compose(rootTransform),
                    modelState.isUvLocked()
            );
        }

        // ── Базовый слой ──────────────────────────────────────────────────────
        TextureAtlasSprite baseSprite = spriteGetter.apply(context.getMaterial("base"));

        @SuppressWarnings("unchecked")
        List<BakedQuad>[] baseQuads = (List<BakedQuad>[]) Array.newInstance(List.class, 6);

        for (Direction face : Direction.values()) {
            int faceIdx = face.get3DDataValue();
            baseQuads[faceIdx] = new ArrayList<>(4);
            for (int quad = 0; quad < 4; quad++) {
                BlockElement el  = this.baseElements[faceIdx][quad];
                BlockElementFace bf = el.faces.get(face);
                baseQuads[faceIdx].add(
                        UnbakedGeometryHelper.bakeElementFace(el, bf, baseSprite, face, modelState, modelLocation)
                );
            }
        }

        // ── Оверлейный слой ───────────────────────────────────────────────────
        // sprites[0] = overlay (без соседей), sprites[1] = overlay_connected (CTM атлас)
        TextureAtlasSprite[] overlaySprites = {
                spriteGetter.apply(context.getMaterial("overlay")),
                spriteGetter.apply(context.getMaterial("overlay_connected"))
        };

        BakedQuad[][][] quads = new BakedQuad[6][4][ConnectionLogic.values().length];

        for (Direction face : Direction.values()) {
            int faceIdx = face.get3DDataValue();
            for (int quad = 0; quad < 4; quad++) {
                for (ConnectionLogic logic : ConnectionLogic.values()) {
                    BlockElement el = this.faceElements[faceIdx][quad][logic.ordinal()];
                    BlockElementFace bf = el.faces.get(face);
                    quads[faceIdx][quad][logic.ordinal()] = UnbakedGeometryHelper.bakeElementFace(
                            el, bf,
                            logic.chooseTexture(overlaySprites),
                            face, modelState, modelLocation
                    );
                }
            }
        }

        // ── render_type ───────────────────────────────────────────────────────
        ResourceLocation rtHint = context.getRenderTypeHint();
        RenderTypeGroup renderTypes = (rtHint != null)
                ? context.getRenderType(rtHint)
                : RenderTypeGroup.EMPTY;

        return new CastleKeyDoorBakedModel(
                baseQuads, quads,
                spriteGetter.apply(context.getMaterial("particle")),
                overrides, context.getTransforms(), renderTypes
        );
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    private static Vec3i min(Vec3i a, Vec3i b) {
        return new Vec3i(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
    }

    private static Vec3i max(Vec3i a, Vec3i b) {
        return new Vec3i(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
    }

    private static Vector3f toVec3f(Vec3i v) {
        return new Vector3f(v.getX(), v.getY(), v.getZ());
    }
}
