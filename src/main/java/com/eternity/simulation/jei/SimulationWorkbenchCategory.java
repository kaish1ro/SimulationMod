package com.eternity.simulation.jei;

import com.eternity.simulation.ModBlocks;
import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import com.eternity.simulation.menu.SimulationCraftingMenu;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class SimulationWorkbenchCategory implements IRecipeCategory<SimulationWorkbenchRecipe> {

    public static final RecipeType<SimulationWorkbenchRecipe> RECIPE_TYPE =
            RecipeType.create(SimulationMod.MODID, "workbench", SimulationWorkbenchRecipe.class);

    private static final ResourceLocation CRAFTING_TABLE_TEX =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    // Фон: от начала текстуры до нижней границы blueprint-слота (59+18+1=78) + 2px отступ.
    // crafting_table.png содержит стрелку, встроенную прямо в текстуру — рисовать её отдельно НЕ нужно.
    // Разделитель инвентаря игрока находится у y≈82-83 — мы обрезаем до y=80, он не попадает.
    private static final int BG_WIDTH  = 148;
    private static final int BG_HEIGHT = 80;

    private final IDrawable background;
    private final IDrawable icon;

    public SimulationWorkbenchCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(CRAFTING_TABLE_TEX,
                4, 3, BG_WIDTH, BG_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModBlocks.SIMULATION_WORKBENCH.get()));
    }

    @Override
    public RecipeType<SimulationWorkbenchRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.simulation.workbench");
    }

    @Override
    public IDrawable getBackground() { return background; }

    @Override
    public IDrawable getIcon() { return icon; }

    // ── Расположение слотов ───────────────────────────────────────────────────
    // Координаты: позиция слота в меню совпадает с UV в текстуре crafting_table.png.
    // Предмет внутри слота располагается на 1px правее/ниже левого верхнего угла рамки слота,
    // поэтому offset +1 правильный для совмещения с текстурой.

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          SimulationWorkbenchRecipe recipe,
                          IFocusGroup focuses) {

        // Сетка 3×3
        List<Ingredient> ingredients = recipe.craftingRecipe().getIngredients();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int i = col + row * 3;
                var slot = builder.addSlot(RecipeIngredientRole.INPUT,
                        SimulationCraftingMenu.GRID_X + col * 18 - 4,
                        SimulationCraftingMenu.GRID_Y + row * 18 - 3);
                if (i < ingredients.size()) {
                    slot.addIngredients(VanillaTypes.ITEM_STACK,
                            List.of(ingredients.get(i).getItems()));
                }
            }
        }

        // Blueprint-слот (под стрелкой, в свободной области между результатом и инвентарём)
        ItemStack blueprintStack = switch (recipe.blueprintGroup()) {
            case 1  -> new ItemStack(ModItems.BLUEPRINT_GROUP1.get());
            case 2  -> new ItemStack(ModItems.BLUEPRINT_GROUP2.get());
            default -> new ItemStack(ModItems.BLUEPRINT_GROUP3.get());
        };
        builder.addSlot(RecipeIngredientRole.INPUT,
                        SimulationCraftingMenu.BLUEPRINT_X - 4,
                        SimulationCraftingMenu.BLUEPRINT_Y - 3)
                .addItemStack(blueprintStack);

        // Результат
        net.minecraft.core.RegistryAccess registryAccess =
                Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess()
                : net.minecraft.core.RegistryAccess.EMPTY;
        builder.addSlot(RecipeIngredientRole.OUTPUT,
                        SimulationCraftingMenu.RESULT_X - 4,
                        SimulationCraftingMenu.RESULT_Y - 3)
                .addItemStack(recipe.craftingRecipe().getResultItem(registryAccess));
    }

    // draw() не переопределяем — стрелка уже встроена в текстуру crafting_table.png
    // и отображается как часть фона. Рисовать её отдельно не нужно (иначе будет двойная стрелка).
}
