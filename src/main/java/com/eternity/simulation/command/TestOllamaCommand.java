package com.eternity.simulation.command;

import com.eternity.simulation.config.SimulationConfig;
import com.eternity.simulation.ollama.OllamaClient;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class TestOllamaCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testollama")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();

                    source.sendSystemMessage(Component.literal(
                            "§7[Ollama] §fПроверяю соединение..."));

                    CompletableFuture.runAsync(() -> {
                        OllamaClient client = new OllamaClient();
                        boolean available = client.isAvailable();

                        source.getServer().execute(() -> {
                            if (available) {
                                source.sendSystemMessage(Component.literal(
                                        "§a[Ollama] §fСервер доступен! " +
                                        "Модель: §e" + SimulationConfig.OLLAMA_MODEL.get() +
                                        " §7| Хост: " + SimulationConfig.OLLAMA_HOST.get()));
                            } else {
                                source.sendSystemMessage(Component.literal(
                                        "§c[Ollama] §fСервер недоступен. " +
                                        "Запусти §eollama serve §fи попробуй снова. " +
                                        "§7(Хост: " + SimulationConfig.OLLAMA_HOST.get() + ")"));
                            }
                        });
                    });

                    return 1;
                })
        );
    }
}
