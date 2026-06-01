package com.eternity.simulation;

import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Подписчики на MOD-шину событий (в отличие от ModEvents, который слушает FORGE-шину).
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBus {

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        // SimulationNPC и Observer используют те же атрибуты, что и обычный житель
        event.put(ModEntities.SIMULATION_NPC.get(), Villager.createAttributes().build());
        event.put(ModEntities.OBSERVER.get(),        Villager.createAttributes().build());
        event.put(ModEntities.WANDERER.get(),        Villager.createAttributes().build());
    }
}
