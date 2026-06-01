package com.eternity.simulation.crafting;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeTypes {

    // RecipeType — это vanilla-реестр (не forge), поэтому используем Registries.RECIPE_TYPE
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, SimulationMod.MODID);

    // RecipeSerializer — это forge-реестр
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SimulationMod.MODID);

    public static final RegistryObject<RecipeType<WorkbenchRecipe>> WORKBENCH_CRAFTING =
            RECIPE_TYPES.register("workbench_crafting",
                    () -> RecipeType.simple(
                            new ResourceLocation(SimulationMod.MODID, "workbench_crafting")));

    public static final RegistryObject<RecipeSerializer<WorkbenchRecipe>> WORKBENCH_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("workbench_crafting",
                    WorkbenchRecipeSerializer::new);
}
