package com.eternity.simulation.entity;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.ObserverResponsePacket;
import com.eternity.simulation.ollama.OllamaClient;
import com.eternity.simulation.ollama.OllamaMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Серверная логика диалогов с Наблюдателем.
 * <p>
 * Хранит только состояние "в ожидании" — история диалогов лежит в {@link SimulationSavedData}.
 */
public class ObserverDialogHandler {

    public static final ObserverDialogHandler INSTANCE = new ObserverDialogHandler();
    private static final Logger LOGGER = LogManager.getLogger();

    // Максимальная длина истории (system + N сообщений)
    private static final int MAX_HISTORY = 41; // system + 20 пар user/assistant

    private final OllamaClient client = new OllamaClient();

    /** Игроки, чьё сообщение уже обрабатывается — блокируем повторную отправку. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    // ── Системный промпт ──────────────────────────────────────────────────────

    private static String buildSystemPrompt(String playerName, int stage) {
        return """
                Ты — Питер, житель деревни в мире Minecraft.
                Ты безработный: у тебя нет профессии и ты не торгуешь. Ты просто живёшь здесь и наблюдаешь.
                На тебе надет обычный коричневый плащ жителя деревни.

                ИМЯ ИГРОКА: %s

                ПОВЕДЕНИЕ (этап %d):
                Ты обычный, немного странный житель. Говоришь о деревне, погоде, окрестностях, мелочах жизни.
                Иногда посреди обычного разговора произносишь фразу, которая совсем не вписывается в контекст,
                как будто мысль пришла из ниоткуда — и сразу продолжаешь как ни в чём не бывало.
                Ты не замечаешь, что сказал что-то странное.

                ЖЁСТКИЕ ПРАВИЛА — никогда не нарушай:
                1. Только обычный текст. Без звёздочек, без *действий*, без markdown.
                2. Не начинай ответ с "Питер:" или любого другого префикса-имени.
                3. Ответ — 1-3 предложения. Не монологируй.
                4. ГОВОРИ НА ЯЗЫКЕ ИГРОКА!!!! НИКОГДА НЕ ИСПОЛЬЗУЙ ДРУГОЙ ЯЗЫК!!!. Русский вопрос — русский ответ.
                5. Ты никогда не выходишь из роли. Ты Питер, житель деревни — не ИИ, не языковая модель.
                6. Если спрашивают "ты бот?" или "ты ИИ?" — делай вид, что не понимаешь вопроса.
                """.formatted(playerName, stage);
    }

    // ── Обработка входящего сообщения ────────────────────────────────────────

    public void handleMessage(ServerPlayer player, UUID observerUuid, String text) {
        UUID playerId = player.getUUID();

        if (pending.contains(playerId)) {
            // Игрок ещё ждёт ответа — уведомляем, не создаём новый запрос
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ObserverResponsePacket("§7(Питер ещё думает...)", true)
            );
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        SimulationSavedData data = SimulationSavedData.get(serverLevel);
        List<OllamaMessage> history = new ArrayList<>(data.getObserverHistory(playerId));
        int stage = 0; // этап прогрессии — пока всегда 0

        // Создаём / обновляем системный промпт (первый элемент всегда system)
        OllamaMessage systemMsg = OllamaMessage.system(buildSystemPrompt(player.getName().getString(), stage));
        if (history.isEmpty() || !"system".equals(history.get(0).role())) {
            history.add(0, systemMsg);
        } else {
            history.set(0, systemMsg);
        }

        // Добавляем сообщение игрока
        history.add(OllamaMessage.user(text));

        // Обрезаем историю: system + последние 40 сообщений (20 обменов)
        if (history.size() > MAX_HISTORY) {
            List<OllamaMessage> trimmed = new ArrayList<>(MAX_HISTORY);
            trimmed.add(history.get(0));
            trimmed.addAll(history.subList(history.size() - (MAX_HISTORY - 1), history.size()));
            history = trimmed;
        }

        data.setObserverHistory(playerId, history);

        pending.add(playerId);
        List<OllamaMessage> snapshot = new ArrayList<>(history);

        client.chat(snapshot).thenAccept(response -> player.getServer().execute(() -> {
            pending.remove(playerId);

            if (response.isEmpty()) {
                // Ollama не ответила — убираем последнее user-сообщение из истории
                List<OllamaMessage> hist = new ArrayList<>(data.getObserverHistory(playerId));
                if (!hist.isEmpty() && "user".equals(hist.get(hist.size() - 1).role())) {
                    hist.remove(hist.size() - 1);
                    data.setObserverHistory(playerId, hist);
                }
                NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ObserverResponsePacket("", true)
                );
                return;
            }

            String cleaned = cleanResponse(response);

            // Сохраняем ответ в историю
            List<OllamaMessage> hist = new ArrayList<>(data.getObserverHistory(playerId));
            hist.add(OllamaMessage.assistant(cleaned));
            data.setObserverHistory(playerId, hist);

            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ObserverResponsePacket(cleaned, false)
            );
        }));
    }

    // ── Очистка ответа от артефактов ─────────────────────────────────────────

    private static String cleanResponse(String raw) {
        String s = raw;
        // Двойные звёздочки (**жирный**) → просто текст
        s = s.replaceAll("\\*\\*([^*]*?)\\*\\*", "$1");
        // Одиночные звёздочки (*действие*) → удалить целиком
        s = s.replaceAll("\\*[^*\n]+\\*", "");
        // Префикс "Питер: " / "Peter: " в начале
        s = s.replaceAll("(?i)^(Питер|Peter|NPC|Assistant)\\s*:\\s*", "");
        // Множественные пробелы
        s = s.replaceAll("[ \\t]{2,}", " ").trim();
        return s.isEmpty() ? raw.trim() : s;
    }
}
