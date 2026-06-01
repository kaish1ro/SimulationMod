package com.eternity.simulation.client;

import com.eternity.simulation.SimulationMod;
import com.eternity.simulation.entity.RiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Рендерер разлома с кастомными текстурами.
 *
 * <p>Текстуры: {@code assets/simulation/textures/entity/rift1-4.png}
 * Размер: 512×64 пикселя (8:1, под пропорции 50×5 блоков).
 * Элитный разлом автоматически масштабируется ×2.
 */
public class RiftRenderer extends EntityRenderer<RiftEntity> {

    private static final Map<RiftEntity.RiftType, RenderType> RENDER_TYPES =
            new EnumMap<>(RiftEntity.RiftType.class);

    static {
        for (RiftEntity.RiftType type : RiftEntity.RiftType.values()) {
            ResourceLocation tex = new ResourceLocation(
                    SimulationMod.MODID, "textures/entity/" + type.textureName + ".png");
            RENDER_TYPES.put(type, RenderType.entityCutoutNoCull(tex));
        }
    }

    public RiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity entity) {
        // Используется только для name tags; реальная текстура — через RENDER_TYPES
        return new ResourceLocation(SimulationMod.MODID,
                "textures/entity/" + entity.getRiftType().textureName + ".png");
    }

    @Override
    public boolean shouldRender(RiftEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true; // noCulling=true на сущности, это дополнительная страховка
    }

    @Override
    public void render(RiftEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float closingProgress = entity.getClosingProgress();
        float halfLength = (entity.getLength() / 2f) * (1f - closingProgress);
        float halfHeight = entity.getHeight() / 2f;

        if (halfLength < 0.05f) return;

        poseStack.pushPose();

        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getFixedYaw()));

        VertexConsumer vc = bufferSource.getBuffer(
                RENDER_TYPES.getOrDefault(entity.getRiftType(),
                        RENDER_TYPES.get(RiftEntity.RiftType.RED)));
        PoseStack.Pose  pose = poseStack.last();
        Matrix4f        mat  = pose.pose();
        Matrix3f        nm   = pose.normal();

        float L = halfLength;
        float H = halfHeight;

        // entityCutoutNoCull отключает back-face culling — одна грань видна с обеих сторон.
        // Вершины: нижний-левый → нижний-правый → верхний-правый → верхний-левый
        vtx(vc, mat, nm,  -L, -H, 0,   0f, 1f,  packedLight,  0, 0, 1);
        vtx(vc, mat, nm,   L, -H, 0,   1f, 1f,  packedLight,  0, 0, 1);
        vtx(vc, mat, nm,   L,  H, 0,   1f, 0f,  packedLight,  0, 0, 1);
        vtx(vc, mat, nm,  -L,  H, 0,   0f, 0f,  packedLight,  0, 0, 1);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void vtx(VertexConsumer vc, Matrix4f mat, Matrix3f nm,
                             float x, float y, float z,
                             float u, float v, int light,
                             float nx, float ny, float nz) {
        vc.vertex(mat, x, y, z)
          .color(255, 255, 255, 255)
          .uv(u, v)
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(light)
          .normal(nm, nx, ny, nz)
          .endVertex();
    }
}
