package com.eternity.simulation;

import com.eternity.simulation.config.SimulationConfig;
import com.eternity.simulation.crafting.ModRecipeTypes;
import com.eternity.simulation.menu.ModMenuTypes;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.structures.ModStructures;
import com.eternity.simulation.world.ModBiomeModifiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SimulationMod.MODID)
public class SimulationMod {
    public static final String MODID = "simulation";

    public SimulationMod() {
        // GameRules регистрируются при первой загрузке класса
        ModGameRules.class.getName();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ITEMS.register(modBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenuTypes.MENU_TYPES.register(modBus);
        ModBiomeModifiers.BIOME_MODIFIER_SERIALIZERS.register(modBus);
        ModStructures.STRUCTURE_TYPES.register(modBus);
        ModStructures.STRUCTURE_PIECE_TYPES.register(modBus);
        ModRecipeTypes.RECIPE_TYPES.register(modBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modBus);
        ModCreativeTabs.CREATIVE_TABS.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SimulationConfig.SPEC, "simulation.toml");

        NetworkHandler.register();
    }
}
