package com.eternity.simulation.entity;

import com.eternity.simulation.ollama.NpcConversationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

public class SimulationNPC extends Villager {

    private static final int CONVERSATION_DISTANCE = 10;

    public SimulationNPC(EntityType<? extends Villager> type, Level level) {
        super(type, level, VillagerType.PLAINS);
    }

    // ── Взаимодействие (ПКМ) ─────────────────────────────────────────────────

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        NpcConversationManager mgr = NpcConversationManager.INSTANCE;

        if (mgr.isPlayerTalkingToThisNpc(serverPlayer, this)) {
            mgr.endConversation(serverPlayer);
        } else if (mgr.isNpcBusy(this)) {
            serverPlayer.sendSystemMessage(Component.literal("§cЭтот NPC сейчас занят разговором."));
        } else if (mgr.isInConversation(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.literal("§cВы уже ведёте диалог с другим NPC."));
        } else {
            mgr.startConversation(serverPlayer, this);
        }

        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    // ── Запрет торговли ───────────────────────────────────────────────────────

    @Override
    public void openTradingScreen(Player player, Component displayName, int level) {
        // Торговля отключена — NPC общается через чат
    }

    @Override
    public MerchantOffers getOffers() {
        return new MerchantOffers();
    }

    // ── Завершение диалога при гибели NPC ────────────────────────────────────

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide()) return;

        NpcConversationManager mgr = NpcConversationManager.INSTANCE;
        ServerPlayer partner = mgr.getConversingPlayer(this, level());
        if (partner != null) {
            mgr.endConversation(partner);
            partner.sendSystemMessage(Component.literal("§7[Диалог] NPC погиб. Разговор завершён."));
        }
    }

    // ── Проверка дистанции каждую секунду ────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || tickCount % 20 != 0) return;

        NpcConversationManager mgr = NpcConversationManager.INSTANCE;
        ServerPlayer partner = mgr.getConversingPlayer(this, level());
        if (partner != null && distanceTo(partner) > CONVERSATION_DISTANCE) {
            mgr.endConversation(partner);
            partner.sendSystemMessage(Component.literal("§7[Диалог] Вы отошли слишком далеко."));
        }
    }

    // ── Системный промпт ──────────────────────────────────────────────────────

    public String getSystemPrompt() {
        String name = hasCustomName() ? getCustomName().getString() : "NPC";
        return "You are " + name + ", a mysterious NPC in a Minecraft modpack called 'The Simulation'. " +
               "The world around you may not be what it seems — it could be a simulation. " +
               "STRICT RULES — follow every rule exactly:\n" +
               "1. Reply with PLAIN TEXT ONLY. No asterisks, no *actions*, no _underscores_, no **bold**, no markdown of any kind.\n" +
               "2. Do NOT start your reply with your name or any label (never write '" + name + ":' or 'NPC:').\n" +
               "3. Be concise: 1-3 sentences per reply.\n" +
               "4. Respond in the SAME LANGUAGE the player uses. If they write in Russian — reply in Russian. If in English — reply in English.\n" +
               "5. Stay in character at ALL times. You are " + name + " — not an AI, not a language model. Never break character.";
    }
}
