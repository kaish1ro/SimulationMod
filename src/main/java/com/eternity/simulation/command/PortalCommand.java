package com.eternity.simulation.command;

import com.eternity.simulation.SimulationSavedData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class PortalCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("simportal")
                .requires(src -> src.hasPermission(2))

                .then(Commands.literal("unlock")
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());
                        data.setPortalWipUnlocked(true);
                        data.setDragonDefeated(true);
                        src.sendSuccess(() -> Component.literal(
                            "§a[SimPortal] §fWIP-блокировка снята, дракон отмечен как убитый. Все измерения открыты."), true);
                        return 1;
                    }))

                .then(Commands.literal("lock")
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());
                        data.setPortalWipUnlocked(false);
                        src.sendSuccess(() -> Component.literal(
                            "§c[SimPortal] §fWIP-блокировка восстановлена. Доступен только Сумеречный Лес."), true);
                        return 1;
                    }))

                .then(Commands.literal("status")
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        SimulationSavedData data = SimulationSavedData.get(src.getServer().overworld());
                        boolean unlocked = data.isPortalWipUnlocked();
                        boolean dragon   = data.isDragonDefeated();
                        boolean nether   = data.hasEnteredNether();
                        src.sendSuccess(() -> Component.literal(
                            "§e[SimPortal] §fСостояние порталов:\n" +
                            "§7  Незер посещён:     §f" + nether + "\n" +
                            "§7  Дракон убит:       §f" + dragon + "\n" +
                            "§7  WIP разблокирован: §f" + unlocked + "\n" +
                            "§7  Открыто: §f" +
                            (dragon ? (unlocked ? "все измерения" : "только Сумеречный Лес") : "нет модовых")), false);
                        return 1;
                    }))
        );
    }
}
