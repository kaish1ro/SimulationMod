package com.eternity.simulation.client;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.SimulationMod;
import com.eternity.simulation.menu.ModMenuTypes;
import com.eternity.simulation.screen.SimulationWorkbenchScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SimulationMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
            MenuScreens.register(ModMenuTypes.SIMULATION_WORKBENCH.get(),
                    SimulationWorkbenchScreen::new)
        );
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Оба NPC выглядят как обычные жители (текстуру заменим позже)
        event.registerEntityRenderer(ModEntities.SIMULATION_NPC.get(), VillagerRenderer::new);
        event.registerEntityRenderer(ModEntities.OBSERVER.get(),        VillagerRenderer::new);
        event.registerEntityRenderer(ModEntities.WANDERER.get(),        WandererRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT.get(),            RiftRenderer::new);
    }
}
