package com.eternity.simulation.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * Рецепт Технического верстака. Оборачивает ShapedRecipe и добавляет
 * поле blueprintGroup (1 / 2 / 3), определяющее, какой Blueprint нужен.
 * blueprintGroup == 0 означает, что Blueprint не требуется.
 */
public class WorkbenchRecipe implements net.minecraft.world.item.crafting.Recipe<CraftingContainer> {

    private final ShapedRecipe inner;
    private final int blueprintGroup;

    public WorkbenchRecipe(ShapedRecipe inner, int blueprintGroup) {
        this.inner = inner;
        this.blueprintGroup = blueprintGroup;
    }

    // ── Делегируем всё в inner ShapedRecipe ───────────────────────────────────

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return inner.matches(container, level);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return inner.assemble(container, registryAccess);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return inner.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return inner.getResultItem(registryAccess);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return inner.getIngredients();
    }

    @Override
    public ResourceLocation getId() {
        return inner.getId();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.WORKBENCH_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.WORKBENCH_CRAFTING.get();
    }

    @Override
    public boolean isSpecial() {
        return inner.isSpecial();
    }

    @Override
    public String getGroup() {
        return inner.getGroup();
    }

    // ── Дополнительные поля ───────────────────────────────────────────────────

    public ShapedRecipe getInner() {
        return inner;
    }

    public int getBlueprintGroup() {
        return blueprintGroup;
    }

    public int getRecipeWidth() {
        return inner.getWidth();
    }

    public int getRecipeHeight() {
        return inner.getHeight();
    }
}
