package com.eternity.simulation.jei;

import net.minecraft.world.item.crafting.CraftingRecipe;

/**
 * Обёртка над CraftingRecipe для JEI-категории Технического верстака.
 * blueprintGroup = 1/2/3 — группа схем, которая требуется для этого рецепта.
 */
public record SimulationWorkbenchRecipe(CraftingRecipe craftingRecipe, int blueprintGroup) {}
