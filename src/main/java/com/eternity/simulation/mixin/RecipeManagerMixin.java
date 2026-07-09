package com.eternity.simulation.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Guard/Knight Summoner (Legendary Monsters) убраны из прогрессии по просьбе
 * пользователя — крафты должны исчезнуть полностью, включая рецепт "активации"
 * деактивированной версии предмета (deactivated_guard_summoner/
 * deactivated_knight_summoner — впрочем, у этих двух самих по себе НЕТ ни
 * рецепта, ни лута, так что без верхних двух рецептов они и так недостижимы).
 *
 * <p>Не датапак-оверрайд (замена JSON того же пути в своих ресурсах) — тот
 * способ зависит от порядка слияния мод-ресурспаков между legendary_monsters
 * и simulation, который ничем не гарантирован. Вместо этого чистим уже
 * СМЕРЖЕННУЮ карту рецептов после {@code RecipeManager.apply()} — результат
 * не зависит от того, чья версия файла победила при слиянии датапаков.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    private static final Set<ResourceLocation> SIMULATION$BLOCKED_RECIPES = Set.of(
            new ResourceLocation("legendary_monsters", "guard_summoner"),
            new ResourceLocation("legendary_monsters", "guard_summoner_activate"),
            new ResourceLocation("legendary_monsters", "soul_summoner"),
            new ResourceLocation("legendary_monsters", "soul_summoner_activate"));

    @Shadow private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;
    @Shadow private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(method = "apply", at = @At("TAIL"))
    private void simulation$removeSummonerRecipes(Map<ResourceLocation, ?> object,
                                                   ResourceManager resourceManager,
                                                   ProfilerFiller profiler, CallbackInfo ci) {
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : this.recipes.entrySet()) {
            Map<ResourceLocation, Recipe<?>> inner = new HashMap<>(entry.getValue());
            inner.keySet().removeAll(SIMULATION$BLOCKED_RECIPES);
            newRecipes.put(entry.getKey(), inner);
        }
        this.recipes = newRecipes;

        Map<ResourceLocation, Recipe<?>> newByName = new HashMap<>(this.byName);
        newByName.keySet().removeAll(SIMULATION$BLOCKED_RECIPES);
        this.byName = newByName;
    }
}
