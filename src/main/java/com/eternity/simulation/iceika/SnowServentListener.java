package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * threateningly_mobs:snow_servent появляется как декоративный эмбиент возле
 * башни Лича ({@link LichTowerAmbientSpawner}) — без дропа, чисто атмосферный
 * моб. Чистим его обычный (не MCreator-процедурный на этот раз, обычный
 * minecraft:entity loot table) дроп через событие, а не переопределением
 * лут-таблицы — порядок слияния датапаков между модами не гарантирован.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class SnowServentListener {

    private static final ResourceLocation SNOW_SERVENT_ID =
            new ResourceLocation("threateningly_mobs", "snow_servent");

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (ForgeRegistries.ENTITY_TYPES.getValue(SNOW_SERVENT_ID) != event.getEntity().getType()) return;
        event.getDrops().clear();
    }
}
