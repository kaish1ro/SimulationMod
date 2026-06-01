package com.eternity.simulation.items;

import com.eternity.simulation.ModEvents;
import com.eternity.simulation.ModGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class BlueprintItem extends Item {
    private final int group;

    public int getGroup() { return group; }

    // Флаг: идёт ли в данный момент перезагрузка датапаков.
    // Пока reload не завершён — повторные ПКМ игнорируются во избежание краша.
    private static volatile boolean reloadInProgress = false;

    public BlueprintItem(Properties properties, int group) {
        super(properties);
        this.group = group;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (reloadInProgress) return InteractionResultHolder.pass(player.getItemInHand(hand));

        ServerLevel serverLevel = (ServerLevel) level;
        MinecraftServer server = serverLevel.getServer();

        GameRules.Key<GameRules.BooleanValue> ruleKey = switch (group) {
            case 1 -> ModGameRules.BLUEPRINT_GROUP1;
            case 2 -> ModGameRules.BLUEPRINT_GROUP2;
            default -> ModGameRules.BLUEPRINT_GROUP3;
        };

        // getBooleanSafe: getRule() может вернуть null в некоторых состояниях сервера
        if (getBooleanSafe(serverLevel.getGameRules(), ruleKey)) {
            player.sendSystemMessage(Component.literal("§7Эта группа схем уже открыта."));
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        // Открываем группу глобально
        GameRules.BooleanValue rule = serverLevel.getGameRules().getRule(ruleKey);
        if (rule == null) {
            // Ключ не найден — не можем открыть группу, молча игнорируем
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        rule.set(true, server);

        // Обновляем глобальный scoreboard (#world) — KubeJS читает его в ServerEvents.recipes
        ModEvents.setGlobalScore(server.getScoreboard(), group, true);

        String groupDesc = switch (group) {
            case 1 -> "I §8— Базовые машины, Солнечные панели I-II";
            case 2 -> "II §8— Продвинутые машины, Солнечные панели III-V";
            default -> "III §8— Элитные машины, Солнечные панели VI-VIII";
        };
        server.getPlayerList().getPlayers().forEach(p ->
            p.sendSystemMessage(Component.literal("§fОткрыта группа схем §e" + groupDesc))
        );

        player.getItemInHand(hand).shrink(1);

        // Перезагружаем датапаки асинхронно. reloadInProgress предотвращает повторные краши.
        reloadInProgress = true;
        server.reloadResources(server.getPackRepository().getSelectedIds())
              .thenRun(() -> reloadInProgress = false);

        return InteractionResultHolder.success(ItemStack.EMPTY);
    }

    private static boolean getBooleanSafe(GameRules rules, GameRules.Key<GameRules.BooleanValue> key) {
        GameRules.BooleanValue value = rules.getRule(key);
        return value != null && value.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Схемы каких-то механизмов, неизвестные вам")
            .withStyle(ChatFormatting.ITALIC));
        tooltip.add(Component.literal("§8ПКМ чтобы изучить"));
    }
}
