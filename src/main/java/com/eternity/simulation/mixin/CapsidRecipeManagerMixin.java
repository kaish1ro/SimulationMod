package com.eternity.simulation.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.alexthe666.alexsmobs.misc.CapsidRecipeManager", remap = false)
public abstract class CapsidRecipeManagerMixin {
    private static final ResourceLocation SHATTERED_DIMENSIONAL_CARVER =
        new ResourceLocation("alexsmobs", "shattered_dimensional_carver");

    @Inject(method = "lambda$apply$0", at = @At("HEAD"), cancellable = true)
    private static void simulation$removeShatteredDimensionalCarverRecipe(
        ImmutableMap.Builder<ResourceLocation, ?> builder,
        ResourceLocation id,
        JsonElement json,
        CallbackInfo ci
    ) {
        if (SHATTERED_DIMENSIONAL_CARVER.equals(id)) {
            ci.cancel();
        }
    }
}
