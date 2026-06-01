package com.eternity.simulation.entity;

import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.OpenObserverDialogPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Скиталец — сквозной AI NPC.
 *
 * <p>Конечный автомат:
 * <ul>
 *   <li>APPROACHING — спавнится вдали, самостоятельно идёт к цели (30 сек таймаут)</li>
 *   <li>WAITING     — стоит рядом, ждёт ПКМ; через 10 сек после приветствия уходит</li>
 *   <li>IN_DIALOG   — диалог открыт, заморожен на месте</li>
 *   <li>LEAVING     — прощается и исчезает с частицами</li>
 * </ul>
 */
public class WandererEntity extends Villager {

    // ── Скриптованные фразы (несколько вариантов) ────────────────────────────

    public static final List<String> GREETINGS = List.of(
        "Подожди. Мне нужно с тобой поговорить.",
        "Хорошо, что наткнулся на тебя. Есть кое-что, что хочу обсудить.",
        "Наконец-то живой человек. Есть разговор.",
        "Не уходи. Есть кое-что важное."
    );

    public static final List<String> IGNORE_PHRASES = List.of(
        "Не хочешь говорить? Понимаю.",
        "Ещё не готов? Ничего страшного.",
        "Хорошо. Я подожду другого раза."
    );

    public static final String FAREWELL = "Ещё увидимся.";

    /** Фразы когда Скиталец не смог дойти до игрока (APPROACHING → timeout). */
    public static final List<String> MISSED_PHRASES = List.of(
        "Хм, показалось...",
        "Странно. Был здесь только что.",
        "Наверное, ветер.",
        "Чудится всякое в дороге."
    );

    // ── Тайминги (в тиках) ───────────────────────────────────────────────────

    /** Максимум тиков в APPROACHING до принудительного ухода: 30 сек. */
    private static final int APPROACH_TIMEOUT = 600;
    /** Расстояние (блоки), при котором считается "подошёл": 4 блока. */
    private static final double APPROACH_DISTANCE = 4.0;

    /** Задержка до приветствия после перехода в WAITING: 2 секунды. */
    private static final int GREETING_DELAY = 40;
    /** Таймаут ожидания ПКМ после приветствия: 10 секунд. */
    private static final int WAIT_TIMEOUT   = GREETING_DELAY + 200;
    /** Время от прощания до исчезновения: 3 секунды. */
    private static final int LEAVE_DURATION = 60;

    // ── Тайминги сцены выхода (тики) ─────────────────────────────────────────

    /** Реплика 1: приветствие. */
    private static final int SCENE_T1 = 60;
    /** Реплика 2: про другие миры. */
    private static final int SCENE_T2 = 140;
    /** Реплика 3: про записи. */
    private static final int SCENE_T3 = 220;
    /** Реплика 4: передаёт книгу. */
    private static final int SCENE_T4 = 300;
    /** Прощальная реплика → LEAVING. */
    private static final int SCENE_T5 = 380;

    /** Скриптованный монолог сцены выхода из Края. */
    private static final String[] SCENE_LINES = {
        "Ты сделал это. Мало кто заходит так далеко.",
        "За пределами этого мира есть места, куда ещё не ступала нога человека.",
        "Я провёл годы, записывая всё, что удавалось узнать о них.",
        "Возьми это. Может, тебе пригодится больше, чем мне.",
        "Увидимся... когда-нибудь."
    };

    // ── Состояние ────────────────────────────────────────────────────────────

    public enum WandererState { APPROACHING, WAITING, IN_DIALOG, LEAVING, SCENE_MONOLOGUE }

    private WandererState wandererState = WandererState.APPROACHING;
    private int  stateTick      = 0;
    private int  approachTick   = 0;
    private boolean greetingSent  = false;
    private boolean wasIgnored    = false;
    private boolean leaveMsgSent  = false;
    /** true — Скиталец не смог дойти до игрока (APPROACHING timeout). */
    private boolean approachFailed = false;

    // ── Сцена выхода из Края ─────────────────────────────────────────────────

    /** true — этот экземпляр Скитальца запущен в режиме сцены (выход из Края). */
    private boolean sceneMode     = false;
    private int     sceneTick     = 0;
    private boolean sceneBookGiven = false;

    /** Позиция игрока в момент начала монолога — используется для фриза. */
    private double scenePlayerX, scenePlayerY, scenePlayerZ;

    /** Сколько тиков подряд цель не находится. Толерантность к логину/респауну. */
    private int lostTargetTicks = 0;
    /** Сколько тиков терпим отсутствие цели до завершения сцены: 5 секунд. */
    private static final int LOST_TARGET_TIMEOUT = 100;

    /** UUID игрока, к которому пришёл этот экземпляр. */
    private UUID targetPlayerUUID;

    /** Переключить в true для дебаг-сообщений в чат операторам. */
    private static final boolean DEBUG_CHAT = false;

    // ── Конструктор ──────────────────────────────────────────────────────────

    public WandererEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level, VillagerType.PLAINS);
        this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.NONE));
        this.setInvulnerable(true);
        this.setCustomName(Component.literal("Скиталец").withStyle(ChatFormatting.DARK_PURPLE));
        this.setCustomNameVisible(true);
    }

    // ── AI ────────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ApproachPlayerGoal(this));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    // ── Назначение целевого игрока ────────────────────────────────────────────

    public void setTargetPlayer(UUID playerUUID) {
        this.targetPlayerUUID = playerUUID;
    }

    /**
     * Активирует режим сцены выхода из Края.
     * Должен вызываться до добавления сущности в мир (или сразу после).
     * После подхода к игроку Скиталец произнесёт монолог и отдаст книгу.
     */
    public void startScene() {
        sceneMode = true;
    }

    // ── ПКМ по сущности ───────────────────────────────────────────────────────

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        // Разрешаем диалог только целевому игроку
        if (!sp.getUUID().equals(targetPlayerUUID)) {
            sp.sendSystemMessage(Component.literal("§8Скиталец смотрит сквозь тебя, как будто тебя нет."));
            return InteractionResult.FAIL;
        }

        if (wandererState == WandererState.WAITING) {
            startDialog(sp);
        } else if (wandererState == WandererState.IN_DIALOG) {
            // Повторный клик — переоткрываем окно
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sp),
                new OpenObserverDialogPacket(this.getUUID(), getCustomName().getString())
            );
        }

        return InteractionResult.sidedSuccess(false);
    }

    private void startDialog(ServerPlayer sp) {
        wandererState = WandererState.IN_DIALOG;
        this.setNoAi(true);
        this.getNavigation().stop();
        this.getLookControl().setLookAt(sp, 30f, 30f);

        String name = getCustomName() != null ? getCustomName().getString() : "Скиталец";
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> sp),
            new OpenObserverDialogPacket(this.getUUID(), name, true)  // autoFirstMessage = true
        );
    }

    /** Вызывается из {@code CloseNpcDialogPacket} когда игрок закрыл экран. */
    public void onDialogClose() {
        wasIgnored = false;
        startLeaving();
    }

    // ── Запрет торговли ───────────────────────────────────────────────────────

    @Override
    public void openTradingScreen(Player player, Component displayName, int level) {}

    @Override
    public MerchantOffers getOffers() { return new MerchantOffers(); }

    @Override
    public boolean canBeLeashed(Player player) { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    // ── Заглушки звуков жителя ────────────────────────────────────────────────

    @Override protected SoundEvent getAmbientSound()                    { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source)   { return null; }
    @Override protected SoundEvent getDeathSound()                      { return null; }
    @Override protected SoundEvent getTradeUpdatedSound(boolean isGood){ return null; }
    @Override public    SoundEvent getNotifyTradeSound()                { return null; }
    @Override protected float getSoundVolume()                          { return 0f;   }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        stateTick++;

        switch (wandererState) {
            case APPROACHING     -> tickApproaching();
            case WAITING         -> tickWaiting();
            case IN_DIALOG       -> tickInDialog();
            case LEAVING         -> tickLeaving();
            case SCENE_MONOLOGUE -> tickSceneMonologue();
        }
    }

    private void tickApproaching() {
        // Дебаг раз в 100 тиков чтобы не спамить
        if (approachTick == 0) {
            debugChat("APPROACHING → идёт к игроку (макс " + APPROACH_TIMEOUT / 20 + "с)");
        }
        approachTick++;

        ServerPlayer target = getTargetPlayer();
        if (target == null) {
            // В режиме сцены терпим кратковременную потерю цели (респаун/реконнект)
            if (sceneMode && ++lostTargetTicks <= LOST_TARGET_TIMEOUT) {
                return;
            }
            debugChat("APPROACHING → цель пропала, LEAVING");
            approachFailed = true;
            startLeaving();
            return;
        }
        lostTargetTicks = 0;

        double dist = distanceTo(target);

        // Дебаг расстояния каждые 100 тиков
        if (approachTick % 100 == 0) {
            debugChat("APPROACHING: " + String.format("%.1f", dist) + " блоков до цели");
        }

        // Подошли достаточно близко → переходим в WAITING или SCENE_MONOLOGUE
        if (dist <= APPROACH_DISTANCE) {
            this.getNavigation().stop();
            approachTick = 0;
            stateTick    = 0;
            if (sceneMode) {
                wandererState = WandererState.SCENE_MONOLOGUE;
                this.setNoAi(true);
                // Запоминаем позицию игрока для фриза
                ServerPlayer sceneTarget = getTargetPlayer();
                if (sceneTarget != null) {
                    scenePlayerX = sceneTarget.getX();
                    scenePlayerY = sceneTarget.getY();
                    scenePlayerZ = sceneTarget.getZ();
                }
                debugChat("APPROACHING → SCENE_MONOLOGUE");
            } else {
                wandererState = WandererState.WAITING;
                debugChat("APPROACHING → WAITING (подошёл на " + String.format("%.1f", dist) + " блоков)");
            }
            return;
        }

        // Таймаут — не смогли дойти
        if (approachTick >= APPROACH_TIMEOUT) {
            debugChat("APPROACHING → LEAVING (таймаут " + APPROACH_TIMEOUT / 20 + "с, dist="
                    + String.format("%.1f", dist) + ")");
            approachFailed = true;
            startLeaving();
        }
    }

    private void tickWaiting() {
        // Через 2 секунды после перехода в WAITING шлём приветствие
        if (stateTick == GREETING_DELAY && !greetingSent) {
            greetingSent = true;
            sendGreeting();
        }

        // Таймаут — игрок не начал диалог
        if (stateTick >= WAIT_TIMEOUT) {
            wasIgnored = true;
            debugChat("WAITING → LEAVING (проигнорирован)");
            startLeaving();
        }

        lookAtTarget();
    }

    private void tickInDialog() {
        this.setDeltaMovement(0, 0, 0);
        this.getNavigation().stop();
        lookAtTarget();

        // Целевой игрок ушёл далеко или вышел из игры
        ServerPlayer target = getTargetPlayer();
        if (target == null || !target.isAlive() || distanceTo(target) > 20) {
            debugChat("IN_DIALOG → LEAVING (игрок ушёл/умер)");
            startLeaving();
        }
    }

    private void tickLeaving() {
        if (!leaveMsgSent) {
            leaveMsgSent = true;
            if (!sceneMode) {
                sendFarewellMessages();
            }
            spawnLeaveParticles();
        }

        if (stateTick >= LEAVE_DURATION) {
            EncounterManager.INSTANCE.onWandererDespawned(targetPlayerUUID);
            this.remove(RemovalReason.DISCARDED);
        }
    }

    // ── Сцена выхода из Края ─────────────────────────────────────────────────

    /**
     * Скриптованный монолог: Скиталец произносит реплики по таймеру,
     * на 4-й реплике отдаёт книгу, затем уходит.
     */
    private void tickSceneMonologue() {
        sceneTick++;
        lookAtTarget();
        this.setDeltaMovement(0, 0, 0);
        this.getNavigation().stop();

        ServerPlayer target = getTargetPlayer();
        if (target == null) {
            // Толерантность: игрок мог на пару тиков выпасть из списка
            // (респаун после титров, переподключение). Ждём, не убиваем сцену.
            if (++lostTargetTicks > LOST_TARGET_TIMEOUT) {
                startLeaving();
            }
            return;
        }
        lostTargetTicks = 0;

        // ── Фриз игрока: возвращаем на место если ушёл дальше 1 блока ─────────
        // Только если игрок в том же измерении, что и Скиталец — иначе сцену
        // завершаем (игрок ушёл в портал / сменил мир).
        if (target.level() != this.level()) {
            startLeaving();
            return;
        }
        double dx = target.getX() - scenePlayerX;
        double dz = target.getZ() - scenePlayerZ;
        if (dx * dx + dz * dz > 1.0) {
            target.connection.teleport(
                scenePlayerX, scenePlayerY, scenePlayerZ,
                target.getYRot(), target.getXRot());
        }

        if (sceneTick == SCENE_T1) {
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + SCENE_LINES[0]));
        } else if (sceneTick == SCENE_T2) {
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + SCENE_LINES[1]));
        } else if (sceneTick == SCENE_T3) {
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + SCENE_LINES[2]));
        } else if (sceneTick == SCENE_T4) {
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + SCENE_LINES[3]));
            if (!sceneBookGiven) {
                sceneBookGiven = true;
                target.addItem(getWanderersJournal());
            }
        } else if (sceneTick >= SCENE_T5) {
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + SCENE_LINES[4]));
            startLeaving();
        }
    }

    // ── Приветствие / прощание ────────────────────────────────────────────────

    private void sendGreeting() {
        ServerPlayer target = getTargetPlayer();
        if (target == null) return;

        String greeting = GREETINGS.get(level().random.nextInt(GREETINGS.size()));
        target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + greeting));
        target.sendSystemMessage(
            Component.literal("§8(Для диалога нажмите ПКМ)")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        );

        EncounterManager.INSTANCE.onEncounterGreeted(target);
    }

    private void sendFarewellMessages() {
        ServerPlayer target = getTargetPlayer();
        if (target == null) return;

        if (approachFailed) {
            // Не смог дойти — тихо бормочет про себя
            String missed = MISSED_PHRASES.get(level().random.nextInt(MISSED_PHRASES.size()));
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + missed));
            debugChat("Фраза 'показалось': " + missed);
            return; // без обычного прощания
        }

        if (wasIgnored) {
            String ignore = IGNORE_PHRASES.get(level().random.nextInt(IGNORE_PHRASES.size()));
            target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + ignore));
        }
        target.sendSystemMessage(Component.literal("§8[Скиталец] §7" + FAREWELL));
    }

    // ── Частицы при уходе ────────────────────────────────────────────────────

    private void spawnLeaveParticles() {
        if (!(level() instanceof ServerLevel sl)) return;

        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        // Дым — плотная завеса
        sl.sendParticles(ParticleTypes.SMOKE,
            x, y + 0.5, z, 60, 0.4, 0.8, 0.4, 0.02);
        // Большой дым — медленные клубы
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
            x, y + 0.5, z, 20, 0.35, 0.6, 0.35, 0.005);
        // Обратный портал — фиолетовые частицы летят вверх
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
            x, y + 1.0, z, 60, 0.5, 1.0, 0.5, 0.08);
        // Портал — хаотичное кружение
        sl.sendParticles(ParticleTypes.PORTAL,
            x, y + 1.0, z, 50, 0.4, 0.9, 0.4, 0.12);
        // Зачарование — золотые символы разлетаются
        sl.sendParticles(ParticleTypes.ENCHANT,
            x, y + 1.0, z, 40, 0.6, 1.0, 0.6, 0.2);
        // Душа — синие огни у земли
        sl.sendParticles(ParticleTypes.SOUL,
            x, y + 0.2, z, 15, 0.3, 0.2, 0.3, 0.01);
    }

    // ── Книга Скитальца ───────────────────────────────────────────────────────

    /**
     * Возвращает книгу Patchouli «Записки Скитальца».
     * Если Patchouli не загружен — возвращает обычную книгу как запасной вариант.
     */
    private static ItemStack getWanderersJournal() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("patchouli")) {
            try {
                net.minecraft.resources.ResourceLocation bookId =
                        new net.minecraft.resources.ResourceLocation("simulation", "wanderers_journal");
                return vazkii.patchouli.api.PatchouliAPI.get().getBookStack(bookId);
            } catch (Exception e) {
                // Fallback if something goes wrong at runtime
            }
        }
        return new ItemStack(Items.BOOK);
    }

    // ── Вспомогательные ───────────────────────────────────────────────────────

    private void startLeaving() {
        if (wandererState == WandererState.LEAVING) return;
        wandererState = WandererState.LEAVING;
        this.setNoAi(false);
        stateTick = 0;
    }

    private void lookAtTarget() {
        ServerPlayer target = getTargetPlayer();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30f, 30f);
        }
    }

    /** Отправляет дебаг-сообщение всем операторам. Включить: DEBUG_CHAT = true. */
    private void debugChat(String message) {
        if (!DEBUG_CHAT) return;
        if (!(level() instanceof ServerLevel sl)) return;
        net.minecraft.network.chat.Component msg =
            net.minecraft.network.chat.Component.literal("§8[W] §7" + message);
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (sl.getServer().getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(msg);
            }
        }
    }

    ServerPlayer getTargetPlayer() {
        if (targetPlayerUUID == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    }

    public WandererState getWandererState() { return wandererState; }

    // ── Внутренний AI-Goal: движение к игроку ────────────────────────────────

    /**
     * Активен только в состоянии APPROACHING.
     * Использует стандартный pathfinding Villager'а на дистанцию до 60 блоков.
     */
    private static class ApproachPlayerGoal extends Goal {

        private final WandererEntity wanderer;

        ApproachPlayerGoal(WandererEntity wanderer) {
            this.wanderer = wanderer;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return wanderer.wandererState == WandererState.APPROACHING
                && wanderer.getTargetPlayer() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return wanderer.wandererState == WandererState.APPROACHING
                && wanderer.getTargetPlayer() != null;
        }

        @Override
        public void tick() {
            ServerPlayer target = wanderer.getTargetPlayer();
            if (target == null) return;

            // Навигация — moveTo обновляется каждый тик
            // (pathfinding сам кэширует путь, лишних вычислений нет)
            if (wanderer.distanceTo(target) > APPROACH_DISTANCE) {
                wanderer.getNavigation().moveTo(target, 0.5);
            }

            wanderer.getLookControl().setLookAt(target, 30f, 30f);
        }

        @Override
        public void stop() {
            wanderer.getNavigation().stop();
        }
    }
}
