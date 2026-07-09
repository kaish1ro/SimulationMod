package com.eternity.simulation.client;

import com.eternity.simulation.quests.SimulationQuest;
import com.eternity.simulation.quests.SubQuest;
import com.eternity.simulation.quests.SubQuestRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Небольшой прозрачный HUD текущего задания/подзадания — позиция настраивается
 * из {@link QuestScreen} ({@link QuestHudSettings}: сторона экрана + высота).
 * Показывает только текущее (первое незавершённое задание с выполненными
 * зависимостями) — остальные задания игроку тут не видны вообще.
 */
public final class QuestHudOverlay implements IGuiOverlay {

    public static final QuestHudOverlay INSTANCE = new QuestHudOverlay();

    private static final int BG_COLOR     = 0x99101018;
    private static final int ACCENT_COLOR = 0x553FE0D8;
    private static final int TITLE_COLOR  = 0xFF3FE0D8;
    private static final int SUB_COLOR    = 0xFFB9B9D0;
    private static final int BAR_BG_COLOR  = 0x66FFFFFF;
    private static final int BAR_FILL_COLOR = 0xFF3FE0D8;
    private static final int PAD    = 6;
    private static final int MARGIN = 8;
    private static final int BAR_HEIGHT = 4;

    private QuestHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || mc.screen != null) return;
        if (!ClientQuestState.isUiVisible()) return;

        SimulationQuest current = ClientQuestState.getCurrentQuest();
        if (current == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(current.title());
        for (String id : ClientQuestState.getActiveSubQuestIds()) {
            SubQuest sq = SubQuestRegistry.byId(id);
            if (sq == null || !sq.parentQuestId().equals(current.id())) continue;
            int count = ClientQuestState.getSubQuestCount(id);
            lines.add("› " + sq.title() + (count >= 0 ? " (" + count + ")" : ""));
        }

        // Прогресс снятия поля — отдельная полоска, не текстовая строка (только для shield_removal).
        int progress = "shield_removal".equals(current.id())
                ? ClientQuestState.getSubQuestCount(SubQuestRegistry.SHIELD_PROGRESS_PERCENT)
                : -1;

        Font font = mc.font;
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.width(line));

        int boxWidth  = Math.max(textWidth, progress >= 0 ? 120 : 0) + PAD * 2;
        int lineStep  = font.lineHeight + 2;
        int boxHeight = lines.size() * lineStep + PAD * 2 - 2;
        if (progress >= 0) boxHeight += BAR_HEIGHT + 5;

        // Масштаб применяется через матрицу — box*Width/Height ниже уже "физический"
        // (экранный) размер, а всё рисование внутри pushPose идёт в локальных,
        // немасштабированных координатах (0,0)..(boxWidth,boxHeight).
        float scale = (float) QuestHudSettings.getScale();
        int scaledWidth  = Math.round(boxWidth * scale);
        int scaledHeight = Math.round(boxHeight * scale);

        boolean onRight = QuestHudSettings.getSide() == QuestHudSettings.Side.RIGHT;
        int x = onRight ? screenWidth - scaledWidth - MARGIN : MARGIN;
        int maxY = Math.max(MARGIN, screenHeight - scaledHeight - MARGIN);
        int y = MARGIN + (int) ((maxY - MARGIN) * QuestHudSettings.getHeightFraction());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1f);

        guiGraphics.fill(0, 0, boxWidth, boxHeight, BG_COLOR);
        guiGraphics.fill(0, 0, boxWidth, 1, ACCENT_COLOR);

        int ty = PAD;
        guiGraphics.drawString(font, lines.get(0), PAD, ty, TITLE_COLOR, true);
        for (int i = 1; i < lines.size(); i++) {
            ty += lineStep;
            guiGraphics.drawString(font, lines.get(i), PAD, ty, SUB_COLOR, true);
        }

        if (progress >= 0) {
            ty += lineStep + 3;
            int barWidth = boxWidth - PAD * 2;
            int fillWidth = barWidth * Math.max(0, Math.min(100, progress)) / 100;
            guiGraphics.fill(PAD, ty, PAD + barWidth, ty + BAR_HEIGHT, BAR_BG_COLOR);
            if (fillWidth > 0) {
                guiGraphics.fill(PAD, ty, PAD + fillWidth, ty + BAR_HEIGHT, BAR_FILL_COLOR);
            }
        }

        guiGraphics.pose().popPose();
    }
}
