package com.eternity.simulation.ollama;

/**
 * Одно сообщение в диалоге с Ollama.
 *
 * @param role    "system" | "user" | "assistant"
 * @param content текст сообщения
 */
public record OllamaMessage(String role, String content) {

    public static OllamaMessage system(String content)    { return new OllamaMessage("system",    content); }
    public static OllamaMessage user(String content)      { return new OllamaMessage("user",      content); }
    public static OllamaMessage assistant(String content) { return new OllamaMessage("assistant", content); }
}
