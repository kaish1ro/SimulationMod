package com.eternity.simulation.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Экран "загрузки замка" — открывается сервером (см. {@code OpenCastleLoadingScreenPacket})
 * на время работы {@code CastleInterceptTask}, имитируя переход между мирами, пока
 * Final Castle реально перестраивается вокруг игрока. Игрок не может его закрыть сам —
 * закрывается только пакетом {@code CloseCastleLoadingScreenPacket} с сервера.
 */
@OnlyIn(Dist.CLIENT)
public class CastleLoadingScreen extends Screen {

    private static MCEFBrowser browser;

    /**
     * true между openOnClient() и closeOnClient() — переживает даже если сам экран
     * был подменён у нас из-под ног (пауза по Alt+Tab/потере фокуса некоторыми модами
     * вызывает setScreen(PauseScreen) напрямую, минуя нашу защиту от закрытия).
     * Читается тиком в {@link ModKeybinds} — пока флаг true, а текущий экран не наш,
     * экран принудительно открывается заново.
     */
    private static volatile boolean sequenceActive = false;

    public CastleLoadingScreen() {
        super(Component.empty());
    }

    public static boolean isSequenceActive() {
        return sequenceActive;
    }

    public static void openOnClient() {
        sequenceActive = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CastleLoadingScreen) return;
        mc.setScreen(new CastleLoadingScreen());
    }

    public static void closeOnClient() {
        sequenceActive = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CastleLoadingScreen) {
            mc.setScreen(null);
        }
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();

        if (browser == null) {
            browser = MCEF.createBrowser("mod://simulation/castle_loading.html", true, physW, physH);
        } else {
            browser.resize(physW, physH);
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        if (browser != null) browser.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (browser == null) return;
        int texId = browser.getRenderer().getTextureID();
        if (texId == 0) return;

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, texId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Matrix4f mat = guiGraphics.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(mat, 0,     0,      0).uv(0, 0).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, 0,     height, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, width, height, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, width, 0,      0).uv(1, 0).color(255, 255, 255, 255).endVertex();
        tess.end();

        RenderSystem.enableDepthTest();
    }

    // Полностью глушим ввод — экран нельзя закрыть/проскроллить/кликнуть сквозь него.
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return true; }
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return true; }
    @Override
    public boolean charTyped(char c, int modifiers) { return true; }
    @Override
    public boolean mouseClicked(double mx, double my, int btn) { return true; }
    @Override
    public boolean mouseReleased(double mx, double my, int btn) { return true; }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) { return true; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
