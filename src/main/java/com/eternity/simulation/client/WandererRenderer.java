package com.eternity.simulation.client;

import com.eternity.simulation.SimulationMod;
import com.eternity.simulation.entity.WandererEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Рендерер Странника — модель Стива с текстурой wanderer.png.
 */
public class WandererRenderer extends MobRenderer<WandererEntity, HumanoidModel<WandererEntity>> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(SimulationMod.MODID, "textures/entity/wanderer.png");

    public WandererRenderer(EntityRendererProvider.Context ctx) {
        super(ctx,
            new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)),
            0.5f  // радиус тени
        );
    }

    @Override
    public ResourceLocation getTextureLocation(WandererEntity entity) {
        return TEXTURE;
    }
}
