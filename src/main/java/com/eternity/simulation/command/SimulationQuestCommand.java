package com.eternity.simulation.command;

import com.eternity.simulation.quests.QuestSync;
import com.eternity.simulation.quests.SimulationQuest;
import com.eternity.simulation.quests.SimulationQuestRegistry;
import com.eternity.simulation.quests.SimulationQuestState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Плейсхолдер-команда для ручного завершения квестов Симуляции, пока реальные
 * условия выполнения (расположение/триггеры в Финальном замке) не готовы.
 * `/simulationquest complete <id>` — оп-команда, синхронизирует состояние
 * всем игрокам (мод расcчитан на одиночную/кооп-игру).
 */
public class SimulationQuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simulationquest")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("complete")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        SimulationQuest quest = SimulationQuestRegistry.byId(id);
                        if (quest == null) {
                            ctx.getSource().sendFailure(Component.literal("Неизвестный квест: " + id));
                            return 0;
                        }
                        var level = ctx.getSource().getLevel();
                        SimulationQuestState state = SimulationQuestState.get(level.getServer().overworld());
                        boolean changed = state.markCompleted(id);
                        QuestSync.syncMainQuests(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                changed ? "Квест выполнен: " + quest.title()
                                        : "Квест уже был выполнен: " + quest.title()), true);
                        return 1;
                    })))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    var level = ctx.getSource().getLevel();
                    SimulationQuestState state = SimulationQuestState.get(level.getServer().overworld());
                    for (SimulationQuest q : SimulationQuestRegistry.QUESTS) {
                        boolean done = state.isCompleted(q.id());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                (done ? "[x] " : "[ ] ") + q.id() + " — " + q.title()), false);
                    }
                    return 1;
                })));
    }
}
