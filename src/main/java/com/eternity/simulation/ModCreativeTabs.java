package com.eternity.simulation;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimulationMod.MODID);

    public static final RegistryObject<CreativeModeTab> SIMULATION_TAB = CREATIVE_TABS.register(
            "simulation_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.simulation.simulation_tab"))
                    .icon(() -> new ItemStack(ModBlocks.SIMULATION_WORKBENCH.get()))
                    .displayItems((params, output) -> {
                        // ── Осколки симуляции ────────────────────────────────
                        output.accept(ModItems.SIMULATION_SHARD.get());

                        // ── Осколки пространства (дроп из разломов) ──────────
                        output.accept(ModItems.SPACE_SHARD_RED.get());
                        output.accept(ModItems.SPACE_SHARD_BLUE.get());
                        output.accept(ModItems.SPACE_SHARD_PURPLE.get());
                        output.accept(ModItems.SPACE_SHARD_ELITE.get());

                        // ── Пыль пространства ────────────────────────────────
                        output.accept(ModItems.SPACE_DUST_RED.get());
                        output.accept(ModItems.SPACE_DUST_BLUE.get());
                        output.accept(ModItems.SPACE_DUST_PURPLE.get());
                        output.accept(ModItems.SPACE_DUST_ORANGE.get());

                        // ── Кристаллы пространства ───────────────────────────
                        output.accept(ModItems.SPACE_CRYSTAL_RED.get());
                        output.accept(ModItems.SPACE_CRYSTAL_BLUE.get());
                        output.accept(ModItems.SPACE_CRYSTAL_PURPLE.get());
                        output.accept(ModItems.SPACE_CRYSTAL_ORANGE.get());

                        // ── Кластеры пространства ────────────────────────────
                        output.accept(ModItems.SPACE_CLUSTER_RED.get());
                        output.accept(ModItems.SPACE_CLUSTER_BLUE.get());
                        output.accept(ModItems.SPACE_CLUSTER_PURPLE.get());
                        output.accept(ModItems.SPACE_CLUSTER_ORANGE.get());

                        // ── Финальные предметы ───────────────────────────────
                        output.accept(ModItems.SPACE_CRYSTAL_LARGE.get());
                        output.accept(ModItems.SPACE_FRAGMENT.get());
                        output.accept(ModItems.AWAKENED_ETERNAL_CRYSTAL.get());

                        // ── Схемы ────────────────────────────────────────────
                        output.accept(ModItems.BLUEPRINT_GROUP1.get());
                        output.accept(ModItems.BLUEPRINT_GROUP2.get());
                        output.accept(ModItems.BLUEPRINT_GROUP3.get());

                        // ── Блоки ────────────────────────────────────────────
                        output.accept(ModBlocks.SIMULATION_WORKBENCH.get());
                        output.accept(ModBlocks.DOUBLE_COMPRESSED_COBBLESTONE.get());
                        output.accept(ModBlocks.TRIPLE_COMPRESSED_COBBLESTONE.get());
                        output.accept(ModBlocks.QUADRO_COMPRESSED_COBBLESTONE.get());
                    })
                    .build()
    );
}
