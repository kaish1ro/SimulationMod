package com.eternity.simulation.screen;

import com.eternity.simulation.network.CloseNpcDialogPacket;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.ObserverMessagePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Диалоговый экран для общения с NPC (Наблюдатель / Скиталец).
 */
public class ObserverDialogScreen extends Screen {

    // ── Константы макета ─────────────────────────────────────────────────────

    private static final int PANEL_W  = 310;
    private static final int PANEL_H  = 210;
    private static final int HEADER_H = 24;
    private static final int INPUT_H  = 28;
    private static final int PADDING  = 8;
    private static final int LINE_GAP = 2;

    // Цвета (ARGB)
    private static final int COL_OVERLAY  = 0x88_000000;
    private static final int COL_PANEL    = 0xFF_101014;
    private static final int COL_HEADER   = 0xFF_1a1a2e;
    private static final int COL_BORDER   = 0xFF_3a3a4a;
    private static final int COL_SEP      = 0xFF_2a2a3a;
    private static final int COL_NPC      = 0xFF_AAAAAA;
    private static final int COL_PLAYER   = 0xFF_FFD080;
    private static final int COL_OBSERVER = 0xFF_C0C0C0;
    private static final int COL_SYSTEM   = 0xFF_666666;


    // ── Данные ───────────────────────────────────────────────────────────────

    private final UUID observerUuid;
    private final String npcName;

    /**
     * Если true — при первом открытии экрана автоматически отправить «О чём?»
     * (используется Скитальцем, чтобы игрок не печатал первый вопрос вручную).
     */
    private final boolean autoFirstMessage;
    private boolean autoSent = false;

    private final List<Component> rawMessages = new ArrayList<>();
    private final List<WrappedLine> lines = new ArrayList<>();

    private int panelX, panelY;
    private int msgAreaY, msgAreaH, msgAreaW;
    private int lineH;

    private EditBox inputBox;
    private Button  sendButton;

    private boolean isPending = false;
    private int     thinkTick = 0;

    // ── Конструкторы ─────────────────────────────────────────────────────────

    public ObserverDialogScreen(UUID observerUuid, String npcName, boolean autoFirstMessage) {
        super(Component.literal(npcName));
        this.observerUuid     = observerUuid;
        this.npcName          = npcName;
        this.autoFirstMessage = autoFirstMessage;
    }

    /** Обратная совместимость: без авто-отправки (для Наблюдателя). */
    public ObserverDialogScreen(UUID observerUuid, String npcName) {
        this(observerUuid, npcName, false);
    }

    // ── Инициализация ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelX = (this.width  - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        msgAreaW = PANEL_W - PADDING * 2;
        msgAreaY = panelY + HEADER_H + 2;
        msgAreaH = PANEL_H - HEADER_H - 2 - INPUT_H - 2;
        lineH    = this.font.lineHeight + LINE_GAP;

        rewrapLines();

        int inputY = panelY + PANEL_H - INPUT_H + 4;
        int inputW = PANEL_W - PADDING * 2 - 28;

        inputBox = new EditBox(this.font,
            panelX + PADDING, inputY, inputW, 18,
            Component.literal("Напиши что-нибудь..."));
        inputBox.setMaxLength(500);
        inputBox.setBordered(true);
        inputBox.setResponder(text ->
            sendButton.active = !text.isBlank() && !isPending
        );
        this.addRenderableWidget(inputBox);
        this.setInitialFocus(inputBox);

        sendButton = Button.builder(Component.literal("→"), btn -> trySend())
            .bounds(panelX + PADDING + inputW + 4, inputY - 1, 22, 20)
            .build();
        sendButton.active = false;
        this.addRenderableWidget(sendButton);

        // Авто-отправка первого вопроса (только один раз, только при первом init)
        if (autoFirstMessage && !autoSent && rawMessages.isEmpty()) {
            autoSent = true;
            sendAutoMessage("О чём ты хотел поговорить?");
        }
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gg, int mx, int my, float partial) {
        gg.fill(0, 0, this.width, this.height, COL_OVERLAY);
        gg.fill(panelX,        panelY,        panelX + PANEL_W, panelY + PANEL_H, COL_PANEL);

        // Рамка
        gg.fill(panelX,               panelY,               panelX + PANEL_W, panelY + 1,              COL_BORDER);
        gg.fill(panelX,               panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H,         COL_BORDER);
        gg.fill(panelX,               panelY,               panelX + 1,        panelY + PANEL_H,         COL_BORDER);
        gg.fill(panelX + PANEL_W - 1, panelY,               panelX + PANEL_W,  panelY + PANEL_H,         COL_BORDER);

        // Заголовок
        gg.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + HEADER_H, COL_HEADER);
        gg.drawCenteredString(this.font, npcName, panelX + PANEL_W / 2, panelY + 7, COL_NPC);

        // Разделители
        gg.fill(panelX + 4, panelY + HEADER_H,          panelX + PANEL_W - 4, panelY + HEADER_H + 1,          COL_SEP);
        gg.fill(panelX + 4, panelY + PANEL_H - INPUT_H, panelX + PANEL_W - 4, panelY + PANEL_H - INPUT_H + 1, COL_SEP);

        renderMessages(gg);
        super.render(gg, mx, my, partial);
    }

    private void renderMessages(GuiGraphics gg) {
        gg.enableScissor(
            panelX + PADDING, msgAreaY,
            panelX + PADDING + msgAreaW, msgAreaY + msgAreaH
        );

        int visibleLines = msgAreaH / lineH;
        int extraLines   = isPending ? 1 : 0;
        int totalLines   = lines.size() + extraLines;
        int start        = Math.max(0, totalLines - visibleLines);

        int y = msgAreaY;
        for (int i = start; i < lines.size(); i++) {
            WrappedLine wl = lines.get(i);
            gg.drawString(this.font, wl.seq, panelX + PADDING, y, wl.color, false);
            y += lineH;
        }

        if (isPending && (start <= lines.size())) {
            int dots = (thinkTick / 7) % 3 + 1;
            String indicator = "§8" + npcName + " думает" + ".".repeat(dots);
            gg.drawString(this.font, indicator, panelX + PADDING, y, 0xFFFFFF, false);
        }

        gg.disableScissor();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (inputBox != null) inputBox.tick();
        if (isPending) thinkTick++;

    }

    // ── Клавиши ──────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && sendButton.active) {
            trySend();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Отправка сообщения ────────────────────────────────────────────────────

    private void trySend() {
        if (inputBox == null) return;
        String text = inputBox.getValue().trim();
        if (text.isEmpty() || isPending) return;

        addMessage(
            Component.empty()
                .append(Component.literal("Ты: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
            COL_PLAYER
        );

        NetworkHandler.CHANNEL.sendToServer(new ObserverMessagePacket(observerUuid, text));
        inputBox.setValue("");
        isPending = true;
        thinkTick = 0;
        sendButton.active = false;
    }

    /**
     * Отправляет сообщение автоматически (без ввода игрока).
     * Используется для первого вопроса «О чём?» в диалоге со Скитальцем.
     */
    private void sendAutoMessage(String text) {
        addMessage(
            Component.empty()
                .append(Component.literal("Ты: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
            COL_PLAYER
        );
        NetworkHandler.CHANNEL.sendToServer(new ObserverMessagePacket(observerUuid, text));
        isPending = true;
        thinkTick = 0;
        if (sendButton != null) sendButton.active = false;
    }

    // ── Приём ответа от сервера ───────────────────────────────────────────────

    /**
     * @param response   текст ответа
     * @param isError    true — ошибка Ollama или «ещё думает»
     * @param closeAfter true — диалог завершён, запускаем таймер закрытия
     */
    public void receiveResponse(String response, boolean isError, boolean closeAfter) {
        if (isError && !response.isEmpty()) {
            addMessage(Component.literal(response), COL_SYSTEM);
            return;
        }

        isPending = false;
        thinkTick = 0;

        if (isError) {
            addMessage(
                Component.literal("§c[!] §7Ollama не ответила. Убедись что сервер запущен (ollama serve)."),
                COL_SYSTEM
            );
        } else {
            addMessage(
                Component.empty()
                    .append(Component.literal(npcName + ": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(response).withStyle(ChatFormatting.WHITE)),
                COL_OBSERVER
            );
        }

        if (closeAfter && !isError) {
            // Блокируем ввод — диалог завершён, игрок читает и закрывает сам (ESC)
            if (inputBox   != null) inputBox.setEditable(false);
            if (sendButton != null) sendButton.active = false;
            addMessage(Component.literal("§8— Скиталец уходит —"), COL_SYSTEM);
        } else if (inputBox != null) {
            sendButton.active = !inputBox.getValue().isBlank();
        }
    }

    /** Обратная совместимость — без closeAfter. */
    public void receiveResponse(String response, boolean isError) {
        receiveResponse(response, isError, false);
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private void addMessage(Component msg, int color) {
        rawMessages.add(msg);
        for (FormattedCharSequence seq : this.font.split(msg, msgAreaW)) {
            lines.add(new WrappedLine(seq, color));
        }
    }

    private void rewrapLines() {
        lines.clear();
        for (Component msg : rawMessages) {
            int color = guessColor(msg);
            for (FormattedCharSequence seq : this.font.split(msg, msgAreaW)) {
                lines.add(new WrappedLine(seq, color));
            }
        }
    }

    private static int guessColor(Component msg) {
        String plain = msg.getString();
        if (plain.startsWith("Ты:"))  return COL_PLAYER;
        if (plain.contains("[!]"))    return COL_SYSTEM;
        if (plain.startsWith("§"))    return COL_SYSTEM;
        return COL_OBSERVER;
    }

    // ── Прочее ───────────────────────────────────────────────────────────────

    @Override
    public void removed() {
        NetworkHandler.CHANNEL.sendToServer(new CloseNpcDialogPacket(observerUuid));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    // ── Внутренний тип ───────────────────────────────────────────────────────

    private record WrappedLine(FormattedCharSequence seq, int color) {}
}
