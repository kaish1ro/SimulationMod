package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import com.eternity.simulation.network.CloseCastleLoadingScreenPacket;
import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.OpenCastleLoadingScreenPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Перехват игрока в момент сжигания шипов Лампой Углей (см. {@code LampOfCindersMixin}):
 * замораживаем игрока, показываем экран загрузки (имитация перехода между мирами) и
 * в это время автоматически перестраиваем канонический Final Castle на месте, где он
 * уже сгенерирован ванильно. Игрок в итоге оказывается в той же точке, но вокруг —
 * уже настоящий замок.
 *
 * <p>Порядок сборки — по спецификации: clear → terrainfill → (корпус замка) →
 * towers (достраивает лестницу в лабиринт) → (лабиринт) → forcefield.
 * Каждый батчевый под-шаг уже существует как отдельная команда/Task-класс —
 * этот класс только последовательно их дёргает и ждёт {@code isRunning()==false}.
 */
public final class CastleInterceptTask {

    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("simulation.CastleIntercept");

    private enum Stage { IDLE, COUNTDOWN, OPEN_SCREEN, CLEAR, TERRAINFILL, CASTLE, TOWERS, LABYRINTH, FORCEFIELD, RESTORE }

    private static Stage stage = Stage.IDLE;
    private static ServerPlayer target;
    private static ServerLevel tfLevel;
    private static BlockPos anchor;
    private static int countdownTicks;
    private static double savedX, savedY, savedZ;
    private static float savedYaw, savedPitch;
    private static List<CastleDataMarker> pendingMarkers;

    private CastleInterceptTask() {}

    public static boolean isRunning() { return stage != Stage.IDLE; }

    /** Вызывается из {@code LampOfCindersMixin} (и командой {@code /simcastle intercept} для теста). */
    public static void onThornBurned(ServerPlayer player) {
        if (isRunning()) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (data.isCastleInterceptTriggered()) return;
        if (!data.hasCastleAnchor()) {
            LOGGER.warn("[Intercept] Шипы сожжены, но якорь замка ещё не захвачен — перехват пропущен.");
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(CastleConstants.TWILIGHT_FOREST_DIM)) {
            return;
        }

        data.setCastleInterceptTriggered(true);
        data.setCastleLockdownActive(true);

        target = player;
        tfLevel = level;
        anchor = data.getCastleAnchorPos();
        pendingMarkers = new ArrayList<>();

        savedX = player.getX();
        savedY = player.getY();
        savedZ = player.getZ();
        savedYaw = player.getYRot();
        savedPitch = player.getXRot();

        player.setInvulnerable(true);
        player.sendSystemMessage(Component.literal("§7Думал это конец?"));

        countdownTicks = 100; // 5 секунд драматической паузы перед экраном загрузки
        stage = Stage.COUNTDOWN;
        LOGGER.info("[Intercept] Перехват запущен игроком {}", player.getName().getString());
    }

    public static void tick() {
        if (stage == Stage.IDLE) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        SimulationSavedData data = SimulationSavedData.get(server.overworld());

        // Заморозка: удерживаем игрока на месте весь перехват, кроме финального восстановления.
        if (target != null && target.isAlive() && stage != Stage.RESTORE) {
            target.teleportTo(savedX, savedY, savedZ);
            target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }

        switch (stage) {
            case COUNTDOWN -> {
                if (countdownTicks-- <= 0) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                        new OpenCastleLoadingScreenPacket());
                    stage = Stage.OPEN_SCREEN;
                    countdownTicks = 10; // небольшой запас, чтобы экран успел открыться на клиенте
                }
            }
            case OPEN_SCREEN -> {
                if (countdownTicks-- <= 0) {
                    CastleClearTask.start(tfLevel, anchor, null);
                    stage = Stage.CLEAR;
                }
            }
            case CLEAR -> {
                if (!CastleClearTask.isRunning()) {
                    CastleTerrainFillTask.start(tfLevel, anchor, null);
                    stage = Stage.TERRAINFILL;
                }
            }
            case TERRAINFILL -> {
                if (!CastleTerrainFillTask.isRunning()) {
                    CastlePlacementTask.placeCastlePart(tfLevel, anchor, pendingMarkers, null);
                    stage = Stage.CASTLE;
                }
            }
            case CASTLE -> {
                // Постановка структуры синхронна (structure-блок) — сразу переходим к towers.
                CastleTowerFixTask.start(tfLevel, anchor, null);
                stage = Stage.TOWERS;
            }
            case TOWERS -> {
                if (!CastleTowerFixTask.isRunning()) {
                    CastlePlacementTask.placeLabyrinthPart(tfLevel, anchor, pendingMarkers, null);
                    stage = Stage.LABYRINTH;
                }
            }
            case LABYRINTH -> {
                // Аналогично CASTLE — синхронная постановка, сразу к forcefield.
                CastlePlacementTask.finalizeBuild(tfLevel, pendingMarkers, null);
                CastleForceFieldTask.start(tfLevel, anchor, null);
                stage = Stage.FORCEFIELD;
            }
            case FORCEFIELD -> {
                if (!CastleForceFieldTask.isRunning()) {
                    finish(data);
                }
            }
            default -> {}
        }
    }

    private static void finish(SimulationSavedData data) {
        stage = Stage.RESTORE;

        data.setCastleEverEntered(true);
        teleportAllPlayersToLabyrinthStart(data);

        if (target != null && target.isAlive()) {
            target.setInvulnerable(false);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new CloseCastleLoadingScreenPacket());
        }

        LOGGER.info("[Intercept] Перестройка завершена.");

        target = null;
        tfLevel = null;
        anchor = null;
        pendingMarkers = null;
        stage = Stage.IDLE;
    }

    /**
     * Замок готов — телепортирует ВСЕХ игроков на сервере (даже тех, кто сейчас не в TF)
     * на маркер {@code 1spawnpoint} (начало лабиринта). Имитация того, что "Бог" разом
     * помещает всех в замок, а не только того, кто сжёг шипы.
     */
    private static void teleportAllPlayersToLabyrinthStart(SimulationSavedData data) {
        BlockPos spawn1 = null;
        for (CastleDataMarker marker : data.getCastleMarkers()) {
            if (marker.has("1spawnpoint")) { spawn1 = marker.pos().below(); break; }
        }
        if (spawn1 == null) {
            LOGGER.warn("[Intercept] Маркер 1spawnpoint не найден — телепорт игроков в лабиринт пропущен.");
            return;
        }

        double tx = spawn1.getX() + 0.5;
        double ty = spawn1.getY();
        double tz = spawn1.getZ() + 0.5;

        for (ServerPlayer p : tfLevel.getServer().getPlayerList().getPlayers()) {
            p.teleportTo(tfLevel, tx, ty, tz, p.getYRot(), p.getXRot());
        }
    }
}
