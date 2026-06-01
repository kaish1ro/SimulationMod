package com.eternity.simulation.ollama;

import com.eternity.simulation.config.SimulationConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный клиент для Ollama REST API.
 *
 * <p>Использует {@link HttpURLConnection} — работает в любой Java-среде,
 * включая Forge с его кастомным classloader'ом.
 * Все запросы выполняются в ForkJoinPool — главный поток не блокируется.
 */
public class OllamaClient {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 120_000;

    // ── Публичное API ─────────────────────────────────────────────────────────

    /**
     * Проверяет доступность Ollama (GET /api/tags).
     * Вызывать из фонового потока.
     */
    public boolean isAvailable() {
        try {
            HttpURLConnection conn = openConnection(host() + "/api/tags", "GET");
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(3_000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            LOGGER.debug("Ollama unavailable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Отправляет список сообщений в Ollama асинхронно.
     *
     * @param messages история диалога
     * @return фьючер с текстом ответа; при ошибке — пустая строка
     */
    public CompletableFuture<String> chat(List<OllamaMessage> messages) {
        String body = buildRequestBody(messages);
        return CompletableFuture.supplyAsync(() -> sendPost(body));
    }

    /**
     * Загружает модель в VRAM асинхронно (keep_alive = -1).
     * Вызывается при спавне Скитальца — модель будет готова к первому запросу.
     */
    public CompletableFuture<Void> loadModel() {
        String body = buildKeepAliveBody(-1);
        return CompletableFuture.runAsync(() -> {
            sendPostGenerate(body);
            LOGGER.info("[Ollama] Model loaded into memory: {}", SimulationConfig.OLLAMA_MODEL.get());
        });
    }

    /**
     * Выгружает модель из VRAM асинхронно (keep_alive = 0).
     * Вызывается когда Скиталец уходит — освобождаем память.
     */
    public CompletableFuture<Void> unloadModel() {
        String body = buildKeepAliveBody(0);
        return CompletableFuture.runAsync(() -> {
            sendPostGenerate(body);
            LOGGER.info("[Ollama] Model unloaded from memory: {}", SimulationConfig.OLLAMA_MODEL.get());
        });
    }

    // ── Внутренняя логика ─────────────────────────────────────────────────────

    private String sendPost(String body) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(host() + "/api/chat", "POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                LOGGER.error("Ollama HTTP {}: {}", status, readStream(conn));
                return "";
            }

            return parseResponse(readStream(conn));

        } catch (Exception e) {
            LOGGER.error("Ollama request failed", e);
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String urlStr, String method) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        return conn;
    }

    private static String readStream(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /** Отправляет POST на /api/generate (используется только для load/unload). */
    private void sendPostGenerate(String body) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(host() + "/api/generate", "POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(10_000); // короткий таймаут — ответ сразу
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode(); // дожидаемся ответа
        } catch (Exception e) {
            LOGGER.warn("[Ollama] load/unload request failed: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Тело запроса для load/unload: только модель и keep_alive. */
    private String buildKeepAliveBody(int keepAlive) {
        JsonObject root = new JsonObject();
        root.addProperty("model", SimulationConfig.OLLAMA_MODEL.get());
        root.addProperty("keep_alive", keepAlive);
        return GSON.toJson(root);
    }

    private String buildRequestBody(List<OllamaMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", SimulationConfig.OLLAMA_MODEL.get());
        root.addProperty("stream", false);
        root.addProperty("keep_alive", -1); // держим модель пока диалог активен

        var arr = new com.google.gson.JsonArray();
        for (OllamaMessage msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            arr.add(m);
        }
        root.add("messages", arr);

        return GSON.toJson(root);
    }

    private String parseResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.getAsJsonObject("message").get("content").getAsString();
        } catch (Exception e) {
            LOGGER.error("Failed to parse Ollama response: {}", json, e);
            return "";
        }
    }

    private String host() {
        return SimulationConfig.OLLAMA_HOST.get();
    }
}
