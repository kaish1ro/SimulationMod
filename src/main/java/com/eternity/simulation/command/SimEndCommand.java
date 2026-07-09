package com.eternity.simulation.command;

import com.eternity.simulation.end.EndIslandDecorator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class SimEndCommand {
    private SimEndCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simend")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("decorate")
                .executes(ctx -> EndIslandDecorator.start(ctx, EndIslandDecorator.defaultRadius()))
                .then(Commands.argument("radius", IntegerArgumentType.integer(
                        EndIslandDecorator.minRadius(), EndIslandDecorator.maxRadius()))
                    .executes(ctx -> EndIslandDecorator.start(
                        ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }
}
