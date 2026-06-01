package com.eternity.simulation.entity;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.ObserverResponsePacket;
import com.eternity.simulation.ollama.OllamaClient;
import com.eternity.simulation.ollama.OllamaMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверная логика диалогов со Скитальцем.
 *
 * <p>Особенности первой встречи:
 * <ul>
 *   <li>Никаких упоминаний симуляции — только одинокий странник, давно не видевший людей</li>
 *   <li>Задаёт 2-3 вопроса и завершает разговор сам (сервер контролирует счётчик)</li>
 *   <li>После последнего ответа посылает клиенту {@code closeAfter=true}
 *       и заставляет сущность уйти</li>
 * </ul>
 */
public class WandererDialogHandler {

    public static final WandererDialogHandler INSTANCE = new WandererDialogHandler();
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int MAX_HISTORY = 21; // короче чем у Наблюдателя — встреча одна

    private final OllamaClient client = new OllamaClient();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    /**
     * Счётчик обменов в текущей сессии диалога.
     * Сбрасывается при завершении диалога. UUID игрока → кол-во ответов NPC.
     */
    private final Map<UUID, Integer> sessionExchanges = new ConcurrentHashMap<>();

    /**
     * Цель для этого диалога — после скольких ответов NPC завершает разговор.
     * Рандомизируется один раз при первом сообщении (2 или 3).
     */
    private final Map<UUID, Integer> sessionTarget = new ConcurrentHashMap<>();

    // ── Системный промпт ──────────────────────────────────────────────────────

    private static String buildSystemPrompt(String playerName, int exchangesDone, int target,
                                            int currentStage, int encounterCount) {
        boolean isLastExchange = (exchangesDone >= target - 1);

        String closingInstruction = isLastExchange
                ? """

      ВАЖНО: это твой последний ответ в этом разговоре.
      Скажи что-то вроде: у тебя мало времени, тебе нужно двигаться дальше, но было приятно поговорить
      НО НЕ ИМЕННО ТАК, меняй последнее сообщение.
      Попрощайся коротко и естественно. Не объясняй куда идёшь.
      """
                : "";

        String stageBehavior = switch(currentStage) {
            case 0, 1 -> "Ты просто путник. Ничего особенного не знаешь о мире. Рад живому общению.";
            case 2, 3 -> "Ты начинаешь замечать странности. Иногда думаешь вслух. Но не уверен.";
            case 4, 5 -> "Ты видел много. Иногда проскальзывают намёки что здесь что-то не так. Но ты осторожен.";
            default -> "Ты знаешь слишком много. Говоришь осторожно, но иногда проговариваешься.";
        };

        String encounterMemory = encounterCount > 0
                ? "Вы встречались %d раз раньше. Помни что игрок тебе говорил.".formatted(encounterCount)
                : "Первый раз встречаешь этого человека.";

        return """
        Ты — Странник. Одинокий путник, который давно бродит по этому блочному миру.
        Ты не помнишь откуда пришёл и не знаешь куда идёшь. Просто идёшь.
        Ты давно не встречал живых людей — месяцы, может больше. Сбился со счёта.
        Тебе не нужна помощь и ты ничего не просишь. Просто рад поговорить.

        МИР: Это мир Minecraft, состоящий из блоков.
        Здесь есть биомы (леса, пустыни, горы), враждебные мобы (зомби, крипер, скелеты).
        Существуют порталы между измерениями (Overworld, Nether, The End).
        Везде разбросаны древние структуры и руины — следы того что было раньше.

        ИМЯ ИГРОКА: %s
        ТЕКУЩИЙ ЭТАП: %d (0-7)
        %s
        %s

        ТВОЁ ПОВЕДЕНИЕ:
        %s
        Ты немного насторожен, но открыт. Говоришь просто, без пафоса.
        Тебе интересен этот человек — кто он, откуда, чем занимается.
        Задаёшь один вопрос за раз. Слушаешь. Иногда делишься своим — коротко.
        
        О МИРЕ: Ты знаешь этот мир. Говоришь про биомы, мобов, структуры что видел.
        Можешь упоминать порталы, измерения, опасности. Это твоя жизнь.
        Но ты НЕ говоришь про "выход" или "побег" если игрок не спросил первым.

        ЖЁСТКИЕ ПРАВИЛА — никогда не нарушай:
        1. Только обычный текст. Никаких звёздочек, markdown, *действий*.
        2. Не начинай ответ с "Странник:", "Скиталец:" или любого префикса.
        3. 1-3 предложения. Ты немногословен.
        4. ГОВОРИ НА ЯЗЫКЕ ИГРОКА. Если пишут по-русски — отвечай по-русски.
        5. Ты никогда не говоришь о симуляции, матрице, программах или ИИ (кроме поздних этапов, и то намёками).
        6. Если спрашивают "ты бот?" — удивись, ты живой человек, просто одинокий.
        7. Если игрок спрашивает про "выход" — ты не знаешь о чём он. Или знаешь но осторожен.
        %s""".formatted(
                playerName,
                currentStage,
                encounterMemory,
                currentStage >= 4 ? "Ты встречал других, которые ищут что-то важное. Может быть и этот." : "",
                stageBehavior,
                closingInstruction
        );
    }

    // ── Обработка сообщения ───────────────────────────────────────────────────

    public void handleMessage(ServerPlayer player, UUID wandererUuid, String text) {
        UUID playerId = player.getUUID();

        if (pending.contains(playerId)) {
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ObserverResponsePacket("§7(Странник задумывается...)", true)
            );
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        SimulationSavedData data = SimulationSavedData.get(serverLevel);
        List<OllamaMessage> history = new ArrayList<>(data.getWandererHistory(playerId));

        // Инициализируем цель диалога для этой сессии (2 или 3 ответа)
        sessionTarget.computeIfAbsent(playerId, k ->
            2 + new Random().nextInt(2) // 2 или 3
        );
        int target   = sessionTarget.get(playerId);
        int done     = sessionExchanges.getOrDefault(playerId, 0);

        // Вычисляем текущий нарративный этап (0-7) из прогресса игрока:
        // до дракона: 0→3 по числу встреч; после дракона: 4→7
        int encounterCount = data.getWandererEncounterCount(playerId);
        int currentStage   = data.isDragonDefeated()
                ? 4 + Math.min(3, encounterCount)
                : Math.min(3, encounterCount);

        // Обновляем системный промпт с учётом текущего прогресса
        OllamaMessage systemMsg = OllamaMessage.system(
            buildSystemPrompt(player.getName().getString(), done, target, currentStage, encounterCount)
        );

        if (history.isEmpty() || !"system".equals(history.get(0).role())) {
            history.add(0, systemMsg);
        } else {
            history.set(0, systemMsg);
        }

        history.add(OllamaMessage.user(text));

        if (history.size() > MAX_HISTORY) {
            List<OllamaMessage> trimmed = new ArrayList<>(MAX_HISTORY);
            trimmed.add(history.get(0));
            trimmed.addAll(history.subList(history.size() - (MAX_HISTORY - 1), history.size()));
            history = trimmed;
        }

        data.setWandererHistory(playerId, history);

        pending.add(playerId);
        List<OllamaMessage> snapshot = new ArrayList<>(history);
        boolean isLastExchange = (done >= target - 1);

        client.chat(snapshot).thenAccept(response -> player.getServer().execute(() -> {
            pending.remove(playerId);

            if (response.isEmpty()) {
                // Ошибка Ollama — откатываем последнее сообщение
                List<OllamaMessage> hist = new ArrayList<>(data.getWandererHistory(playerId));
                if (!hist.isEmpty() && "user".equals(hist.get(hist.size() - 1).role())) {
                    hist.remove(hist.size() - 1);
                    data.setWandererHistory(playerId, hist);
                }
                NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ObserverResponsePacket("", true)
                );
                return;
            }

            String cleaned = cleanResponse(response);

            // Сохраняем ответ в историю
            List<OllamaMessage> hist = new ArrayList<>(data.getWandererHistory(playerId));
            hist.add(OllamaMessage.assistant(cleaned));
            data.setWandererHistory(playerId, hist);

            // Увеличиваем счётчик
            int newCount = done + 1;
            sessionExchanges.put(playerId, newCount);

            if (isLastExchange) {
                // Последний ответ — закрываем диалог
                sessionExchanges.remove(playerId);
                sessionTarget.remove(playerId);

                // Сообщаем клиенту: показать ответ и закрыть экран через ~4 сек
                NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ObserverResponsePacket(cleaned, false, true)
                );

                // Заставляем сущность Скитальца уйти
                Entity entity = serverLevel.getEntity(wandererUuid);
                if (entity instanceof WandererEntity wanderer) {
                    wanderer.onDialogClose();
                }

                LOGGER.debug("[WandererDialogHandler] Dialog ended for {} after {} exchanges",
                        player.getName().getString(), newCount);
            } else {
                // Обычный ответ
                NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ObserverResponsePacket(cleaned, false)
                );
            }
        }));
    }

    /** Вызывается когда игрок закрыл экран вручную — чистим сессию. */
    public void onDialogClosed(UUID playerId) {
        sessionExchanges.remove(playerId);
        sessionTarget.remove(playerId);
    }

    // ── Очистка ответа ────────────────────────────────────────────────────────

    private static String cleanResponse(String raw) {
        String s = raw;
        s = s.replaceAll("\\*\\*([^*]*?)\\*\\*", "$1");
        s = s.replaceAll("\\*[^*\n]+\\*", "");
        s = s.replaceAll("(?i)^(Скиталец|Странник|Wanderer|NPC|Assistant)\\s*:\\s*", "");
        s = s.replaceAll("[ \\t]{2,}", " ").trim();
        return s.isEmpty() ? raw.trim() : s;
    }
}
