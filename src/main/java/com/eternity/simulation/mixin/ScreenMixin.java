package com.eternity.simulation.mixin;

import com.eternity.simulation.client.CustomMenuScreen;
import com.eternity.simulation.client.MenuBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Гасит ванильную землю ({@code renderDirtBackground}) на тех экранах, которые её
 * рисуют, заменяя нашим фоном — чтобы земля не легла поверх фона, который уже
 * нарисован на {@code ScreenEvent.Render.Pre} (см. {@code MenuReplacer}).
 *
 * <p>Очистку кадра и сам фон для ВСЕХ экранов обеспечивает {@code Render.Pre};
 * этот миксин нужен лишь для экранов, активно рисующих ванильный фон. Экраны
 * вроде {@code EditGameRulesScreen} фон не рисуют вовсе, и для них этот инжект
 * не сработает — но им он и не нужен.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true)
    private void simulation$replaceDirt(GuiGraphics g, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof CustomMenuScreen) return;        // у нас собственный браузер-фон
        if (Minecraft.getInstance().level != null) return;   // в игре землю не рисуют
        MenuBackground.draw(g, self.width, self.height);
        ci.cancel();
    }
}
