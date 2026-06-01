package com.eternity.simulation.entity;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.OpenObserverDialogPacket;
import com.eternity.simulation.ollama.OllamaClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

public class ObserverEntity extends Villager {

    /** Радиус (блоков) при котором начинаем прогревать модель Ollama. */
    private static final double WARMUP_RANGE = 10.0;
    /** Тиков без игроков рядом прежде чем выгрузить модель (20 * 30 = 30 сек). */
    private static final int UNLOAD_TICKS = 20 * 30;

    private final OllamaClient ollamaClient = new OllamaClient();
    private boolean modelLoaded = false;
    private int noPlayerTicks = 0;

    public ObserverEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level, VillagerType.PLAINS);
        this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.NONE));
        this.setInvulnerable(true);
        this.setCustomName(Component.literal("Питер").withStyle(ChatFormatting.DARK_GRAY));
        this.setCustomNameVisible(true);
    }

    // ── AI — только осмотр, никакого блуждания ───────────────────────────────

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // ── Тик: управление прогревом модели Ollama ──────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        boolean playerNearby = level().getNearestPlayer(this, WARMUP_RANGE) != null;

        if (playerNearby) {
            noPlayerTicks = 0;
            if (!modelLoaded) {
                ollamaClient.loadModel();
                modelLoaded = true;
            }
        } else {
            noPlayerTicks++;
            if (modelLoaded && noPlayerTicks >= UNLOAD_TICKS) {
                ollamaClient.unloadModel();
                modelLoaded = false;
                noPlayerTicks = 0;
            }
        }
    }

    // ── ПКМ — открыть диалоговый экран ──────────────────────────────────────

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        // Принудительно грузим модель при клике (если ещё не загружена)
        if (!modelLoaded) {
            ollamaClient.loadModel();
            modelLoaded = true;
        }

        String name = getCustomName() != null ? getCustomName().getString() : "Питер";
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> sp),
            new OpenObserverDialogPacket(this.getUUID(), name)
        );
        return InteractionResult.sidedSuccess(false);
    }

    // ── Запрет торговли ───────────────────────────────────────────────────────

    @Override
    public void openTradingScreen(Player player, Component displayName, int level) {
        // намеренно пусто — Питер не торгует
    }

    @Override
    public MerchantOffers getOffers() {
        return new MerchantOffers();
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
