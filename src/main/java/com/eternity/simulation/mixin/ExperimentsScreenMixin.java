package com.eternity.simulation.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code ExperimentsScreen.render()} помимо {@code renderBackground()} (уже
 * перехваченного {@code ScreenMixin}) дополнительно рисует отдельный блит
 * ванильной земли поверх области контента (между хедером и футером). Этот
 * вызов гасим целиком — фон там уже есть, второй блит только портит картинку.
 */
@Mixin(ExperimentsScreen.class)
public abstract class ExperimentsScreenMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void simulation$noDirtContentBlit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        // no-op: фон уже нарисован через renderBackground() -> ScreenMixin
    }
}
