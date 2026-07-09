package com.eternity.simulation.command;

import com.eternity.simulation.balance.WeaponProgression;
import com.eternity.simulation.balance.WeaponUpgrade;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Test command for item-bound weapon upgrade stages. */
public final class BalanceCommand {

    private BalanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simbalance")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("stage")
                .then(Commands.argument("value", IntegerArgumentType.integer(WeaponProgression.MIN_STAGE, WeaponProgression.MAX_STAGE))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int stage = IntegerArgumentType.getInteger(ctx, "value");
                        ItemStack held = player.getMainHandItem();
                        if (held.isEmpty()) {
                            ctx.getSource().sendFailure(Component.literal("Возьми предмет в руку."));
                            return 0;
                        }
                        WeaponUpgrade.setStage(held, stage);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§e[simbalance] §7Ступень апгрейда предмета в руке: §f"
                                + WeaponUpgrade.getStage(held) + "/" + WeaponProgression.MAX_STAGE), true);
                        return 1;
                    })))
            .then(Commands.literal("check")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("Возьми предмет в руку."));
                        return 0;
                    }
                    int stage = WeaponUpgrade.getStage(held);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e[simbalance] §7Ступень предмета в руке: §f"
                            + stage + "/" + WeaponProgression.MAX_STAGE), false);
                    return 1;
                })));
    }
}
