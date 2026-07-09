package com.eternity.simulation.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.eternity.simulation.quests.SimulationQuest;
import com.eternity.simulation.quests.SimulationQuestRegistry;
import com.eternity.simulation.quests.SubQuest;
import com.eternity.simulation.quests.SubQuestRegistry;
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
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;

/**
 * Экран журнала заданий Симуляции (не FTB Quests) — открывается по клавише
 * (см. {@link ModKeybinds}). Пока placeholder-стиль (простой тёмный список),
 * позже заменим HTML/CSS в assets/simulation/html/quests.html на нормальный
 * дизайн без изменений в Java.
 *
 * <p>Собственный CefMessageRouter с ИНЫМИ именами JS-функций
 * (simQuestQuery/simQuestQueryCancel), а не cefQuery/cefQueryCancel как у
 * {@link CustomMenuScreen} — оба роутера регистрируются в одном CEF-клиенте,
 * и одинаковые имена означали бы, что оба браузера дерутся за один и тот же
 * global JS-биндинг.
 */
@OnlyIn(Dist.CLIENT)
public class QuestScreen extends Screen {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("simulation.Quests");

    private static MCEFBrowser browser;
    private static CefMessageRouter messageRouter;
    private static boolean routerAdded = false;

    public QuestScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();

        if (messageRouter == null) {
            CefMessageRouter.CefMessageRouterConfig cfg =
                    new CefMessageRouter.CefMessageRouterConfig("simQuestQuery", "simQuestQueryCancel");
            messageRouter = CefMessageRouter.create(cfg, new CefMessageRouterHandlerAdapter() {
                @Override
                public boolean onQuery(CefBrowser b, CefFrame f, long id,
                                        String request, boolean persistent, CefQueryCallback cb) {
                    handleAction(request, cb);
                    return true;
                }
            });
        }
        if (!routerAdded) {
            MCEF.getClient().getHandle().addMessageRouter(messageRouter);
            routerAdded = true;
        }

        if (browser == null) {
            browser = MCEF.createBrowser("mod://simulation/quests.html", true, physW, physH);
        } else {
            browser.resize(physW, physH);
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        if (browser != null) browser.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    /** Принудительно закрывает окно заданий, если оно сейчас открыто (см. {@link ClientQuestState#applyUiVisibility}). */
    public static void closeIfOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestScreen screen) {
            mc.execute(screen::onClose);
        }
    }

    public static void pushRefreshIfOpen() {
        if (browser == null) return;
        String json = buildQuestsJson();
        String esc = json.replace("\\", "\\\\").replace("'", "\\'");
        browser.executeJavaScript("if(typeof renderQuests==='function')renderQuests('" + esc + "')", "", 0);
    }

    /**
     * Только счётчики активных подзаданий — вызывается на каждое изменение
     * (примерно раз в секунду, пока жива волна/этаж), поэтому НЕ гоняет
     * {@link #pushRefreshIfOpen} (полная пересборка дерева/сайдбара): обновляет
     * только текст у уже существующих элементов, без пересоздания DOM.
     */
    public static void pushCountsUpdateIfOpen() {
        if (browser == null) return;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String id : ClientQuestState.getActiveSubQuestIds()) {
            int count = ClientQuestState.getSubQuestCount(id);
            if (count < 0) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(jsonEscape(id)).append("\":").append(count);
        }
        sb.append("}");
        String esc = sb.toString().replace("\\", "\\\\").replace("'", "\\'");
        browser.executeJavaScript(
                "if(typeof updateSubQuestCounts==='function')updateSubQuestCounts('" + esc + "')", "", 0);
    }

    /**
     * {@code quests} — основной граф (см. {@link SimulationQuestRegistry}).
     * {@code subQuests} — промежуточные задания (см. {@link SubQuestRegistry}):
     * НЕ входят в граф, показываются отдельной строкой под своим parentId.
     */
    private static String buildQuestsJson() {
        StringBuilder sb = new StringBuilder("{\"quests\":[");
        boolean first = true;
        for (SimulationQuest q : SimulationQuestRegistry.QUESTS) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":\"").append(jsonEscape(q.id())).append("\"")
              .append(",\"title\":\"").append(jsonEscape(q.title())).append("\"")
              .append(",\"done\":").append(ClientQuestState.isCompleted(q.id()))
              .append(",\"deps\":[");
            for (int i = 0; i < q.deps().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(jsonEscape(q.deps().get(i))).append("\"");
            }
            sb.append("],\"description\":[");
            for (int i = 0; i < q.description().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(jsonEscape(q.description().get(i))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("],\"subQuests\":[");
        first = true;
        for (String id : ClientQuestState.getActiveSubQuestIds()) {
            SubQuest sq = SubQuestRegistry.byId(id);
            if (sq == null) continue; // неизвестный id — не должно случаться, но не валим рендер
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":\"").append(jsonEscape(sq.id())).append("\"")
              .append(",\"title\":\"").append(jsonEscape(sq.title())).append("\"")
              .append(",\"parentId\":\"").append(jsonEscape(sq.parentQuestId())).append("\"")
              .append(",\"count\":").append(ClientQuestState.getSubQuestCount(sq.id()))
              .append("}");
        }
        return sb.append("]}").toString();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void handleAction(String action, CefQueryCallback callback) {
        if (action.equals("getquests")) {
            callback.success(buildQuestsJson());
            return;
        }
        if (action.equals("close")) {
            callback.success("ok");
            Minecraft.getInstance().execute(this::onClose);
            return;
        }
        if (action.equals("gethudsettings")) {
            callback.success("{\"side\":\""
                    + (QuestHudSettings.getSide() == QuestHudSettings.Side.LEFT ? "left" : "right")
                    + "\",\"height\":" + QuestHudSettings.getHeightFraction()
                    + ",\"scale\":" + QuestHudSettings.getScale() + "}");
            return;
        }
        if (action.startsWith("hudside:")) {
            String side = action.substring("hudside:".length());
            QuestHudSettings.setSide("left".equals(side) ? QuestHudSettings.Side.LEFT : QuestHudSettings.Side.RIGHT);
            callback.success("ok");
            return;
        }
        if (action.startsWith("hudheight:")) {
            try {
                QuestHudSettings.setHeightFraction(Double.parseDouble(action.substring("hudheight:".length())));
                callback.success("ok");
            } catch (NumberFormatException e) {
                callback.failure(-1, "bad height");
            }
            return;
        }
        if (action.startsWith("hudscale:")) {
            try {
                QuestHudSettings.setScale(Double.parseDouble(action.substring("hudscale:".length())));
                callback.success("ok");
            } catch (NumberFormatException e) {
                callback.failure(-1, "bad scale");
            }
            return;
        }
        callback.success("ok");
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

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (browser != null) { browser.sendMousePress(px(mx), py(my), btn); browser.setFocus(true); }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (browser != null) browser.sendMouseRelease(px(mx), py(my), btn);
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (browser != null) browser.sendMouseMove(px(mx), py(my));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (browser != null) browser.sendMouseWheel(px(mx), py(my), delta, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (browser != null) { browser.sendKeyPress(keyCode, (long) scanCode, modifiers); browser.setFocus(true); }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null) browser.sendKeyRelease(keyCode, (long) scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (browser != null && c != 0) browser.sendKeyTyped(c, modifiers);
        return true;
    }

    private int px(double guiX) { return (int) (guiX * minecraft.getWindow().getGuiScale()); }
    private int py(double guiY) { return (int) (guiY * minecraft.getWindow().getGuiScale()); }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
