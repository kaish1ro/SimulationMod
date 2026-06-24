package com.eternity.simulation.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Ищет скрытые точки спавна для Скитальца вне поля зрения игрока.
 *
 * <p>Два режима:
 * <ul>
 *   <li>Поверхность — 30-60 блоков, открытое небо</li>
 *   <li>Подземка  — 15-25 блоков, пещера / тоннель</li>
 * </ul>
 */
public class SpawnHelper {

    // ── Параметры поиска ──────────────────────────────────────────────────────

    private static final int SURFACE_MIN = 30;
    private static final int SURFACE_MAX = 60;
    private static final int UNDERGROUND_MIN = 15;
    private static final int UNDERGROUND_MAX = 25;
    private static final int MAX_ATTEMPTS = 60;

    /**
     * Угол конуса "поле зрения" игрока — точки внутри этого угла пропускаем.
     * cos(65°) ≈ 0.42 — достаточно широкий конус.
     */
    private static final double FOV_COS = 0.42;

    /** Радиус проверки безопасности (нет мобов-монстров рядом). */
    private static final int SAFE_RADIUS = 20;

    /**
     * Максимальная разница по Y между позицией спавна и позицией игрока.
     * Если больше — позиция отбрасывается (игрок в яме, спавн на горе или наоборот).
     */
    private static final int MAX_Y_DIFF = 18;

    /**
     * Глубина ниже поверхности, при которой считаем игрока подземным,
     * даже если над ним есть щель с небом (Y_surface - Y_player > порога).
     */
    private static final int UNDERGROUND_DEPTH_THRESHOLD = 10;

    // ── Публичное API ─────────────────────────────────────────────────────────

    /** Разброс угла вправо/влево от направления взгляда (±30°). */
    private static final double SPAWN_SPREAD_RAD = Math.toRadians(30);

    /**
     * Поверхностная точка спавна: 30-60 блоков в направлении взгляда игрока (±30°),
     * открытое небо, нет монстров рядом.
     *
     * <p>Скиталец появляется впереди, чтобы игрок мог его заметить ещё на подходе.
     */
    public static BlockPos findSurface(ServerPlayer player, ServerLevel level) {
        Random rng = new Random();

        // Горизонтальное направление взгляда игрока
        Vec3 look = player.getLookAngle();
        Vec3 lookH = new Vec3(look.x, 0, look.z);
        if (lookH.lengthSqr() < 1e-6) lookH = new Vec3(1, 0, 0); // фоллбэк
        lookH = lookH.normalize();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Поворачиваем вектор взгляда на случайный угол ±SPAWN_SPREAD_RAD
            double spread = (rng.nextDouble() - 0.5) * 2 * SPAWN_SPREAD_RAD;
            double cos    = Math.cos(spread);
            double sin    = Math.sin(spread);
            double dx     = lookH.x * cos - lookH.z * sin;
            double dz     = lookH.x * sin + lookH.z * cos;

            double dist = SURFACE_MIN + rng.nextDouble() * (SURFACE_MAX - SURFACE_MIN);
            int x = (int)(player.getX() + dx * dist);
            int z = (int)(player.getZ() + dz * dist);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir())                         continue;
            if (!level.getBlockState(pos.below()).isSolid())               continue;
            if (!level.canSeeSky(pos))                                     continue;
            // Не спавним если позиция сильно выше или ниже игрока
            if (Math.abs(y - player.blockPosition().getY()) > MAX_Y_DIFF) continue;

            return pos;
        }

        // Гарантированный фоллбэк — прямо перед игроком, без проверок
        int fx = (int)(player.getX() + lookH.x * SURFACE_MIN);
        int fz = (int)(player.getZ() + lookH.z * SURFACE_MIN);
        int fy = level.getHeight(Heightmap.Types.WORLD_SURFACE, fx, fz);
        return new BlockPos(fx, fy, fz);
    }

    /**
     * Подземная точка спавна: 15-25 блоков в направлении взгляда (±30°),
     * воздушный блок высотой 2, рядом есть стена (пещера).
     */
    public static BlockPos findUnderground(ServerPlayer player, ServerLevel level) {
        Random rng = new Random();
        int playerY = player.blockPosition().getY();

        Vec3 look = player.getLookAngle();
        Vec3 lookH = new Vec3(look.x, 0, look.z);
        if (lookH.lengthSqr() < 1e-6) lookH = new Vec3(1, 0, 0);
        lookH = lookH.normalize();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double spread = (rng.nextDouble() - 0.5) * 2 * SPAWN_SPREAD_RAD;
            double cos    = Math.cos(spread);
            double sin    = Math.sin(spread);
            double dx     = lookH.x * cos - lookH.z * sin;
            double dz     = lookH.x * sin + lookH.z * cos;

            double dist = UNDERGROUND_MIN + rng.nextDouble() * (UNDERGROUND_MAX - UNDERGROUND_MIN);
            int x = (int)(player.getX() + dx * dist);
            int z = (int)(player.getZ() + dz * dist);
            int y = playerY + (rng.nextInt(11) - 5); // ±5 блоков по Y

            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir())         continue;
            if (!level.getBlockState(pos.above()).isAir()) continue;
            if (!hasAdjacentSolid(level, pos))             continue;

            return pos;
        }
        return null;
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    /**
     * Возвращает {@code true} если позиция находится в горизонтальном конусе
     * поля зрения игрока (±65° от направления взгляда).
     */
    public static boolean isInPlayerFOV(ServerPlayer player, BlockPos pos) {
        Vec3 look = player.getLookAngle();
        // Горизонтальный вектор взгляда
        Vec3 lookH = new Vec3(look.x, 0, look.z);
        if (lookH.lengthSqr() < 1e-6) return false;
        lookH = lookH.normalize();

        Vec3 toPos = new Vec3(
            pos.getX() + 0.5 - player.getX(),
            0,
            pos.getZ() + 0.5 - player.getZ()
        );
        if (toPos.lengthSqr() < 1e-6) return true;
        toPos = toPos.normalize();

        double dot = lookH.dot(toPos);
        return dot > FOV_COS; // в поле зрения
    }

    /**
     * Нет ли рядом монстров в радиусе {@value #SAFE_RADIUS} блоков.
     */
    private static boolean isSafeArea(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(
            net.minecraft.world.entity.monster.Monster.class,
            new net.minecraft.world.phys.AABB(pos).inflate(SAFE_RADIUS)
        ).isEmpty();
    }

    /**
     * Есть ли рядом (горизонтально) хотя бы один твёрдый блок — признак пещеры.
     */
    private static boolean hasAdjacentSolid(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.north()).isSolid()
            || level.getBlockState(pos.south()).isSolid()
            || level.getBlockState(pos.east()).isSolid()
            || level.getBlockState(pos.west()).isSolid();
    }

    /**
     * Подземный спавн вплотную — 2-4 блока за спиной игрока (±20°).
     *
     * <p>Используется когда игрок долго под землёй и обычный подземный спавн
     * не может найти подходящую позицию. Скиталец появится прямо за спиной
     * и сразу начнёт подходить — игрок его точно заметит.
     */
    public static BlockPos findBehindClose(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        // Вектор за спину — противоположный взгляду, только горизонталь
        Vec3 behind = new Vec3(-look.x, 0, -look.z);
        if (behind.lengthSqr() < 1e-6) behind = new Vec3(1, 0, 0);
        behind = behind.normalize();

        Random rng = new Random();
        int playerY = player.blockPosition().getY();

        for (int attempt = 0; attempt < 15; attempt++) {
            // Узкий разброс ±20° чтобы точно за спиной
            double spread = (rng.nextDouble() - 0.5) * Math.toRadians(40);
            double cos    = Math.cos(spread);
            double sin    = Math.sin(spread);
            double dx     = behind.x * cos - behind.z * sin;
            double dz     = behind.x * sin + behind.z * cos;

            double dist = 2 + rng.nextDouble() * 2; // 2-4 блока
            int x = (int)(player.getX() + dx * dist);
            int z = (int)(player.getZ() + dz * dist);

            // Ищем воздушное место на уровне игрока и ±1
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(x, playerY + dy, z);
                if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isSolid()) {
                    return pos;
                }
            }
        }

        // Фоллбэк — строго за спиной, 2 блока, без проверок
        return new BlockPos(
            (int)(player.getX() - look.x * 2),
            playerY,
            (int)(player.getZ() - look.z * 2)
        );
    }

    /**
     * Определяет находится ли игрок под землёй.
     *
     * <p>Два условия (любое из них = под землёй):
     * <ul>
     *   <li>Нет неба прямо над головой</li>
     *   <li>Игрок глубже {@value #UNDERGROUND_DEPTH_THRESHOLD} блоков ниже поверхности
     *       (может видеть небо через узкую щель, но фактически в пещере)</li>
     * </ul>
     */
    public static boolean isUnderground(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        if (!player.level().canSeeSky(pos.above())) return true;

        // Глубокий подземный даже если видит щель неба
        if (player.level() instanceof ServerLevel sl) {
            int surfaceY = sl.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            if (surfaceY - pos.getY() > UNDERGROUND_DEPTH_THRESHOLD) return true;
        }

        return false;
    }

    /**
     * Находится ли игрок в воде (плывёт или стоит в воде).
     * При плавании спавн откладывается — позиция нестабильная.
     */
    public static boolean isSwimming(ServerPlayer player) {
        return player.isInWater();
    }

    /**
     * Не находится ли игрок в бою (получал урон от моба последние 5 секунд).
     */
    public static boolean isInCombat(ServerPlayer player) {
        long lastHurt = player.getLastHurtByMobTimestamp();
        return player.level().getGameTime() - lastHurt < 100;
    }
}
