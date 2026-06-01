package com.eternity.simulation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SimulationConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ── Ollama ────────────────────────────────────────────────────────────────
    public static final ForgeConfigSpec.BooleanValue OLLAMA_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_HOST;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_MODEL;

    static {
        BUILDER.push("ollama");

        OLLAMA_ENABLED = BUILDER
                .comment("Enable AI dialogue for NPCs via Ollama")
                .define("enabled", true);

        OLLAMA_HOST = BUILDER
                .comment("Ollama server URL")
                .define("host", "http://127.0.0.1:11434");

        OLLAMA_MODEL = BUILDER
                .comment("Model to use for NPC dialogue")
                .define("model", "llama3.1:8b");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
