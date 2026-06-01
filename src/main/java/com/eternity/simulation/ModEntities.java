package com.eternity.simulation;

import com.eternity.simulation.entity.ObserverEntity;
import com.eternity.simulation.entity.RiftEntity;
import com.eternity.simulation.entity.SimulationNPC;
import com.eternity.simulation.entity.WandererEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SimulationMod.MODID);

    public static final RegistryObject<EntityType<SimulationNPC>> SIMULATION_NPC =
            ENTITY_TYPES.register("simulation_npc", () ->
                    EntityType.Builder.<SimulationNPC>of(SimulationNPC::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("simulation_npc"));

    /** Наблюдатель — сквозной AI NPC сборки. */
    public static final RegistryObject<EntityType<ObserverEntity>> OBSERVER =
            ENTITY_TYPES.register("observer", () ->
                    EntityType.Builder.<ObserverEntity>of(ObserverEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("observer"));

    /** Скиталец — появляется в случайных встречах. */
    public static final RegistryObject<EntityType<WandererEntity>> WANDERER =
            ENTITY_TYPES.register("wanderer", () ->
                    EntityType.Builder.<WandererEntity>of(WandererEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(16)
                            .build("wanderer"));

    /**
     * Разлом — огромная трещина в небе.
     * {@code sized(2f, 6f)} — маленький хитбокс (физика выключена),
     * рендерер рисует визуал на 50 блоков.
     * {@code clientTrackingRange(256)} — виден с очень большого расстояния.
     */
    public static final RegistryObject<EntityType<RiftEntity>> RIFT =
            ENTITY_TYPES.register("rift", () ->
                    EntityType.Builder.<RiftEntity>of(RiftEntity::new, MobCategory.MISC)
                            .sized(2f, 6f)
                            .clientTrackingRange(256)
                            .build("rift"));
}
