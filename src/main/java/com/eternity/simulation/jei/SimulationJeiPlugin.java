package com.eternity.simulation.jei;

import com.eternity.simulation.ModBlocks;
import com.eternity.simulation.crafting.ModRecipeTypes;
import com.eternity.simulation.crafting.WorkbenchRecipe;
import com.eternity.simulation.menu.SimulationCraftingMenu;
import com.eternity.simulation.screen.SimulationWorkbenchScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

@JeiPlugin
public class SimulationJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("simulation", "jei_plugin");
    }

    // ── Регистрация категории ─────────────────────────────────────────────────

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SimulationWorkbenchCategory(guiHelper));
    }

    // ── Регистрация рецептов ──────────────────────────────────────────────────

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<WorkbenchRecipe> workbenchRecipes =
                mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.WORKBENCH_CRAFTING.get());

        List<SimulationWorkbenchRecipe> jeiRecipes = new ArrayList<>();
        for (WorkbenchRecipe recipe : workbenchRecipes) {
            jeiRecipes.add(new SimulationWorkbenchRecipe(recipe.getInner(), recipe.getBlueprintGroup()));
        }

        registration.addRecipes(SimulationWorkbenchCategory.RECIPE_TYPE, jeiRecipes);
    }

    // ── Катализатор ───────────────────────────────────────────────────────────

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.SIMULATION_WORKBENCH.get()),
                SimulationWorkbenchCategory.RECIPE_TYPE);
    }

    // ── Click-area: клик по стрелке в интерфейсе верстака открывает JEI ──────

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(
                SimulationWorkbenchScreen.class,
                SimulationCraftingMenu.RESULT_X - 26,
                SimulationCraftingMenu.RESULT_Y,
                24, 17,
                SimulationWorkbenchCategory.RECIPE_TYPE);
    }
}
