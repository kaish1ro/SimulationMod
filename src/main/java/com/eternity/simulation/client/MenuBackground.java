package com.eternity.simulation.client;

import com.eternity.simulation.SimulationMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Общая отрисовка нашего статичного фона меню. Используется из двух мест:
 * <ul>
 *   <li>{@code MenuReplacer} на событии {@code ScreenEvent.Render.Pre} —
 *       срабатывает каждый кадр ДО отрисовки содержимого любого экрана. Там же
 *       делается GL-очистка кадра ({@link #clearAndDraw}). Это единственный
 *       надёжный способ убрать «призраки»: часть ванильных экранов (например
 *       {@code EditGameRulesScreen}) вообще не рисуют фон и рассчитывают на то,
 *       что кадр уже очищен — а в связке с MCEF главный таргет между кадрами не
 *       очищается, и старое содержимое накапливается.</li>
 *   <li>{@code ScreenMixin} на {@code renderDirtBackground} — гасит ванильную
 *       землю на тех экранах, которые её рисуют, чтобы она не легла поверх
 *       нашего фона ({@link #draw}, без повторной очистки).</li>
 * </ul>
 */
public final class MenuBackground {

    public static final ResourceLocation TEX =
            new ResourceLocation(SimulationMod.MODID, "textures/gui/menu_bg.png");
    private static final int W = 1280, H = 720;

    private MenuBackground() {}

    /** Непрозрачный блит фона на весь экран (без GL-очистки). */
    public static void draw(GuiGraphics g, int width, int height) {
        // Сброс цвета шейдера: если предыдущий рендер (фейд тултипа и т.п.) оставил
        // альфу < 1, наш фон лёг бы полупрозрачно и не перекрыл бы старый кадр.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(TEX, 0, 0, width, height, 0.0F, 0.0F, W, H, W, H);
    }

    /** Принудительная очистка кадра на GPU + блит фона. Зовётся раз за кадр. */
    public static void clearAndDraw(GuiGraphics g, int width, int height) {
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, false);
        draw(g, width, height);
    }
}
