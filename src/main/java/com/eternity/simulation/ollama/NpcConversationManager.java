package com.eternity.simulation.ollama;

import com.eternity.simulation.config.SimulationConfig;
import com.eternity.simulation.entity.SimulationNPC;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class NpcConversationManager {

    public static final NpcConversationManager INSTANCE = new NpcConversationManager();
    private static final Logger LOGGER = LogManager.getLogger();

    private final OllamaClient client = new OllamaClient();

    // Активные диалоги
    private final Map<UUID, UUID> playerToNpc = new ConcurrentHashMap<>(); // playerUUID → npcUUID
    private final Map<UUID, UUID> npcToPlayer = new ConcurrentHashMap<>(); // npcUUID → playerUUID

    // История диалогов (сохраняется между сессиями разговора с одним NPC)
    private final Map<UUID, List<OllamaMessage>> histories = new ConcurrentHashMap<>();

    // Имена NPC (на случай если сущность выгружена)
    private final Map<UUID, String> npcNames = new ConcurrentHashMap<>();

    // Игроки, ожидающие ответа от Ollama
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    // ── Управление диалогом ───────────────────────────────────────────────────

    public void startConversation(ServerPlayer player, SimulationNPC npc) {
        UUID playerId = player.getUUID();
        UUID npcId    = npc.getUUID();
        String npcName = npc.hasCustomName() ? npc.getCustomName().getString() : "NPC";

        playerToNpc.put(playerId, npcId);
        npcToPlayer.put(npcId, playerId);
        npcNames.put(npcId, npcName);

        // Инициализируем историю: при первом знакомстве создаём новую,
        // при повторном — обновляем system prompt (промпт мог измениться, напр. сменили имя NPC).
        String systemPrompt = npc.getSystemPrompt();
        histories.compute(npcId, (k, existing) -> {
            if (existing == null) {
                return new ArrayList<>(List.of(OllamaMessage.system(systemPrompt)));
            }
            // Обновляем первое сообщение (system prompt), сохраняя историю
            if (!existing.isEmpty() && "system".equals(existing.get(0).role())) {
                existing.set(0, OllamaMessage.system(systemPrompt));
            }
            return existing;
        });

        player.sendSystemMessage(Component.literal(
                "§a[Диалог] §fВы начали разговор с §e" + npcName +
                "§f. Пишите в чат. §7(ПКМ — завершить)"));

        broadcastToAll(player.getServer(), Component.literal(
                "§7[Диалог] §f" + player.getName().getString() +
                " §7начинает разговор с §e" + npcName));
    }

    public void endConversation(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID npcId = playerToNpc.remove(playerId);
        if (npcId != null) {
            npcToPlayer.remove(npcId);
            String npcName = npcNames.getOrDefault(npcId, "NPC");
            broadcastToAll(player.getServer(), Component.literal(
                    "§7[Диалог] §f" + player.getName().getString() +
                    " §7завершает разговор с §e" + npcName));
        }
        pendingPlayers.remove(playerId);
    }

    // ── Проверки состояния ────────────────────────────────────────────────────

    public boolean isInConversation(ServerPlayer player) {
        return playerToNpc.containsKey(player.getUUID());
    }

    public boolean isPlayerTalkingToThisNpc(ServerPlayer player, SimulationNPC npc) {
        return npc.getUUID().equals(playerToNpc.get(player.getUUID()));
    }

    public boolean isNpcBusy(SimulationNPC npc) {
        return npcToPlayer.containsKey(npc.getUUID());
    }

    /** Возвращает партнёра NPC по разговору, если тот находится в том же уровне. */
    public ServerPlayer getConversingPlayer(SimulationNPC npc, Level level) {
        UUID playerId = npcToPlayer.get(npc.getUUID());
        if (playerId == null) return null;
        Entity entity = level.getPlayerByUUID(playerId);
        return entity instanceof ServerPlayer sp ? sp : null;
    }

    // ── Обработка сообщения из чата ──────────────────────────────────────────

    public void handleMessage(ServerPlayer player, String text) {
        UUID playerId = player.getUUID();

        if (pendingPlayers.contains(playerId)) {
            player.sendSystemMessage(Component.literal("§c[Диалог] NPC ещё думает, подожди..."));
            return;
        }

        UUID npcId = playerToNpc.get(playerId);
        if (npcId == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        String npcName   = npcNames.getOrDefault(npcId, "NPC");
        String playerName = player.getName().getString();
        List<OllamaMessage> history = histories.get(npcId);
        if (history == null) return;

        // Показываем сообщение игрока всем
        broadcastToAll(server, Component.literal(
                "§6[Диалог] §e" + playerName + " §7→ §e" + npcName + "§f: " + text));

        // Проверяем, включён ли Ollama
        if (!SimulationConfig.OLLAMA_ENABLED.get()) {
            broadcastToAll(server, Component.literal(
                    "§e[" + npcName + "]§c: (AI-диалоги отключены в simulation.toml)"));
            return;
        }

        broadcastToAll(server, Component.literal("§7[Диалог] §o" + npcName + " думает..."));

        history.add(OllamaMessage.user(text));
        pendingPlayers.add(playerId);

        List<OllamaMessage> snapshot = new ArrayList<>(history);

        client.chat(snapshot).thenAccept(response -> server.execute(() -> {
            pendingPlayers.remove(playerId);

            if (response.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "§c[Диалог] Ошибка: Ollama не ответила. " +
                        "Убедись, что сервер запущен (ollama serve)."));
                // Убираем неудавшееся сообщение из истории
                history.remove(history.size() - 1);
                return;
            }

            String cleaned = cleanResponse(response, npcName);
            history.add(OllamaMessage.assistant(cleaned));
            broadcastToAll(server, Component.literal("§e[" + npcName + "]§f: " + cleaned));
        }));
    }

    // ── Очистка ответа от артефактов модели ──────────────────────────────────

    /**
     * Убирает markdown-мусор, который llama3.1 вставляет по умолчанию:
     * <ul>
     *   <li>{@code *действие*} — роле-плей действия со звёздочками</li>
     *   <li>{@code **жирный**} — markdown-жирный шрифт</li>
     *   <li>{@code Имя: ...} — префикс имени NPC в начале строки</li>
     * </ul>
     */
    private static String cleanResponse(String raw, String npcName) {
        String s = raw;

        // 1. Убираем **жирный** — сначала двойные, чтобы не смешать с одиночными
        s = s.replaceAll("\\*\\*([^*]*?)\\*\\*", "$1");

        // 2. Убираем *действия* целиком (включая сам текст)
        s = s.replaceAll("\\*[^*\n]+\\*", "");

        // 3. Убираем возможный префикс "NPC: " или "Имя NPC: " в начале
        if (!npcName.isEmpty()) {
            s = s.replaceAll("(?i)^" + Pattern.quote(npcName) + "\\s*:\\s*", "");
        }
        s = s.replaceAll("(?i)^(NPC|Assistant|AI|Bot)\\s*:\\s*", "");

        // 4. Схлопываем множественные пробелы/переносы в один пробел
        s = s.replaceAll("[ \\t]{2,}", " ")
             .replaceAll("(\\r?\\n){2,}", "\n")
             .trim();

        // Если после чистки ничего не осталось — возвращаем оригинал как есть
        return s.isEmpty() ? raw.trim() : s;
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private void broadcastToAll(MinecraftServer server, Component message) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
