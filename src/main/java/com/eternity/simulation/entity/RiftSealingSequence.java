package com.eternity.simulation.entity;

import com.eternity.simulation.ModEntities;
import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Последовательность запечатывания разломов.
 *
 * <p>Таймлайн (тики):
 * <pre>
 *   0–59  — лучи частиц из фонарей
 *   60    — молнии по кристаллу и фонарям
 *   80    — очищаем блоки + взрыв + синий разлом + дождь
 *   82    — Inferno ("Тиран из неизвестного мира"), bossbar, агрессия
 *   84    — разлом → CLOSING
 *   100   — active = false; боссбар продолжает до смерти босса
 * </pre>
 */
public class RiftSealingSequence {

    public static final RiftSealingSequence INSTANCE = new RiftSealingSequence();
    private static final Logger LOGGER = LogManager.getLogger("simulation.RiftSealing");

    // ── ID ────────────────────────────────────────────────────────────────────

    public static final ResourceLocation AURORA_BLOCK   = new ResourceLocation("twilightforest",     "aurora_block");
    public static final ResourceLocation CRYSTAL_BLOCK  = new ResourceLocation("threateningly_mobs", "magic_crystal_block");
    public static final ResourceLocation INFERNO_ENTITY = new ResourceLocation("threateningly_mobs", "the_inferno");

    // ── Структура (относительно magic_crystal_block) ─────────────────────────
    // Колонка 3: 0 = aurora_block, 1 = sea_lantern

    private static final int[][] STRUCTURE = {
        // Центр -1 и -2
        {  0, -1,  0, 0 }, {  0, -2,  0, 0 },
        // Крест (3 блока в 4 стороны)
        {  1,-2, 0,0},{  2,-2, 0,0},{  3,-2, 0,0},
        { -1,-2, 0,0},{ -2,-2, 0,0},{ -3,-2, 0,0},
        {  0,-2,-1,0},{  0,-2,-2,0},{  0,-2,-3,0},
        {  0,-2, 1,0},{  0,-2, 2,0},{  0,-2, 3,0},
        // Столб Восток
        { 3,-1,0,0},{ 3,0,0,0},{ 3,1,0,0},{ 3,2,0,1},
        // Столб Запад
        {-3,-1,0,0},{-3,0,0,0},{-3,1,0,0},{-3,2,0,1},
        // Столб Север
        { 0,-1,-3,0},{ 0,0,-3,0},{ 0,1,-3,0},{ 0,2,-3,1},
        // Столб Юг
        { 0,-1, 3,0},{ 0,0, 3,0},{ 0,1, 3,0},{ 0,2, 3,1},
    };

    // ── Состояние ─────────────────────────────────────────────────────────────

    private boolean   active       = false;
    private long      startTick    = 0;
    private BlockPos  crystalPos   = null;
    private List<BlockPos> lanternPositions = new ArrayList<>();

    // Босс (живёт дольше active-фазы)
    private UUID           infernoUUID = null;
    private ServerBossEvent infernoBar = null;

    /** true — нужно тикать (либо идёт ритуал, либо босс ещё жив). */
    public boolean needsTick() { return active || infernoUUID != null; }

    // ── Валидация ─────────────────────────────────────────────────────────────

    public static boolean validate(ServerLevel level, BlockPos crystalPos) {
        var aurora  = ForgeRegistries.BLOCKS.getValue(AURORA_BLOCK);
        if (aurora == null) { LOGGER.error("aurora_block not found!"); return false; }

        for (int[] row : STRUCTURE) {
            BlockPos check    = crystalPos.offset(row[0], row[1], row[2]);
            var expectedBlock = (row[3] == 1) ? Blocks.SEA_LANTERN : aurora;
            if (!level.getBlockState(check).is(expectedBlock)) return false;
        }
        return true;
    }

    // ── Запуск ───────────────────────────────────────────────────────────────

    public void start(ServerLevel level, BlockPos crystalPos) {
        if (active) return;
        this.active     = true;
        this.startTick  = level.getGameTime();
        this.crystalPos = crystalPos;
        lanternPositions.clear();
        for (int[] row : STRUCTURE) {
            if (row[3] == 1) lanternPositions.add(crystalPos.offset(row[0], row[1], row[2]));
        }
        LOGGER.info("[RiftSealing] Started at {}", crystalPos);
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();

        // Обновляем боссбар (всегда, пока босс жив)
        tickBossBar(overworld);

        if (!active) return;

        long elapsed = overworld.getGameTime() - startTick;

        if (elapsed < 60) {
            tickBeams(overworld);

        } else if (elapsed == 60) {
            // Молнии
            strikeLightning(overworld);

        } else if (elapsed == 80) {
            // Взрыв + разлом + дождь
            clearAndExplode(overworld);
            spawnRift(overworld);
            overworld.setWeatherParameters(0, 12_000, true, false);

        } else if (elapsed == 82) {
            // Босс
            doSpawnInferno(overworld);

        } else if (elapsed == 84) {
            // Закрываем разлом
            closeNearbyRift(overworld);

        } else if (elapsed >= 100) {
            active = false;
            crystalPos = null;
            lanternPositions.clear();
            LOGGER.info("[RiftSealing] Ritual phase complete.");
        }
    }

    // ── Боссбар ───────────────────────────────────────────────────────────────

    private void tickBossBar(ServerLevel level) {
        if (infernoUUID == null || infernoBar == null) return;

        Entity e = level.getEntity(infernoUUID);
        if (e instanceof LivingEntity living && living.isAlive()) {
            infernoBar.setProgress(living.getHealth() / living.getMaxHealth());
            // Добавляем новых игроков, которые зашли
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                infernoBar.addPlayer(p);
            }
            // Имя над головой не должно появляться (страховка на случай если мод его ставит)
            if (living.isCustomNameVisible()) living.setCustomNameVisible(false);
            // Постоянная агрессия: если цель пропала/мертва/далеко — нацеливаем на ближайшего
            if (living instanceof Mob mob) {
                LivingEntity tgt = mob.getTarget();
                if (tgt == null || !tgt.isAlive() || !(tgt instanceof ServerPlayer)) {
                    ServerPlayer nearest = nearestPlayer(level,
                            living.getX(), living.getY(), living.getZ());
                    if (nearest != null) mob.setTarget(nearest);
                }
            }
        } else {
            // Босс погиб
            infernoBar.removeAllPlayers();
            infernoBar  = null;
            infernoUUID = null;
            LOGGER.info("[RiftSealing] Inferno defeated, bossbar removed.");
        }
    }

    // ── Эффекты ───────────────────────────────────────────────────────────────

    private void tickBeams(ServerLevel level) {
        for (BlockPos lantern : lanternPositions) {
            for (int i = 0; i < 20; i++) {
                double px = lantern.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
                double py = lantern.getY() + 1.0 + i * 0.8;
                double pz = lantern.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
                level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0.05, 0, 0.02);
            }
        }
        if (crystalPos != null) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(level.random.nextInt(360));
                double r = 1.5 + level.random.nextDouble();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        crystalPos.getX() + 0.5 + Math.cos(angle) * r,
                        crystalPos.getY() + 0.5,
                        crystalPos.getZ() + 0.5 + Math.sin(angle) * r,
                        1, 0, 0.1, 0, 0.05);
            }
        }
    }

    private void strikeLightning(ServerLevel level) {
        if (crystalPos == null) return;
        // Молния по кристаллу
        spawnBolt(level, crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
        // Молния по каждому фонарю
        for (BlockPos lp : lanternPositions) {
            spawnBolt(level, lp.getX() + 0.5, lp.getY(), lp.getZ() + 0.5);
        }
    }

    /** Убирает все блоки структуры вручную (надёжно), затем взрыв для эффекта. */
    private void clearAndExplode(ServerLevel level) {
        if (crystalPos == null) return;
        // Убираем кристалл
        level.setBlock(crystalPos, Blocks.AIR.defaultBlockState(), 3);
        // Убираем все блоки структуры
        for (int[] row : STRUCTURE) {
            level.setBlock(crystalPos.offset(row[0], row[1], row[2]),
                    Blocks.AIR.defaultBlockState(), 3);
        }
        // Взрыв для звука и частиц (блоки уже убраны, визуал всё равно будет)
        double cx = crystalPos.getX() + 0.5;
        double cy = crystalPos.getY();
        double cz = crystalPos.getZ() + 0.5;
        level.explode(null, cx, cy, cz, 4.0f, false,
                net.minecraft.world.level.Level.ExplosionInteraction.TNT);
    }

    private static void spawnBolt(ServerLevel level, double x, double y, double z) {
        var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(x, y, z);
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);
    }

    private RiftEntity spawnRift(ServerLevel level) {
        if (crystalPos == null) return null;
        RiftEntity rift = ModEntities.RIFT.get().create(level);
        if (rift == null) return null;
        rift.moveTo(crystalPos.getX() + 0.5,
                    crystalPos.getY() + 10,
                    crystalPos.getZ() + 0.5, 0f, 0f);
        rift.setRiftType(RiftEntity.RiftType.BLUE);
        level.addFreshEntity(rift);
        return rift;
    }

    /** Ближайший живой игрок к точке (или null). */
    private static ServerPlayer nearestPlayer(ServerLevel level, double x, double y, double z) {
        return level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.level() == level && p.isAlive())
                .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(x, y, z)))
                .orElse(null);
    }

    private void doSpawnInferno(ServerLevel level) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(INFERNO_ENTITY);
        if (type == null) { LOGGER.error("the_inferno not found!"); return; }

        Entity entity = type.create(level);
        if (entity == null) return;

        double cx = crystalPos != null ? crystalPos.getX() + 0.5 : 0;
        double cy = crystalPos != null ? crystalPos.getY() + 10  : 64;
        double cz = crystalPos != null ? crystalPos.getZ() + 0.5 : 0;

        entity.moveTo(cx, cy, cz, 0f, 0f);
        // НЕ ставим customName на сущность — рендерер моба показывает плашку при
        // наличии customName независимо от флага видимости. Имя только в боссбаре.
        entity.addTag("sealed_rift_boss");

        // Агрессия сразу после спавна
        if (entity instanceof Mob mob) {
            ServerPlayer nearest = nearestPlayer(level, cx, cy, cz);
            if (nearest != null) mob.setTarget(nearest);
        }

        level.addFreshEntity(entity);

        // HP + урон — базовые значения × скейл игроков
        double scale = com.eternity.simulation.ModEvents.getBossScale(level);
        if (entity instanceof LivingEntity living) {
            var maxHp = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHp != null) {
                double hp = 300.0 * scale;
                maxHp.setBaseValue(hp);
                living.setHealth((float) hp);
            }
            var dmg = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * scale);
        }

        // Боссбар (синий)
        infernoUUID = entity.getUUID();
        infernoBar  = new ServerBossEvent(
                Component.literal("Тиран из неизвестного мира"),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            infernoBar.addPlayer(p);
        }
        LOGGER.info("[RiftSealing] Inferno spawned, bossbar created.");
    }

    private void closeNearbyRift(ServerLevel level) {
        if (crystalPos == null) return;
        double cx = crystalPos.getX() + 0.5;
        double cy = crystalPos.getY() + 10;
        double cz = crystalPos.getZ() + 0.5;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof RiftEntity rift && rift.isAlive()
                    && rift.distanceToSqr(cx, cy, cz) < 400) {
                rift.forceClose();
                break;
            }
        }
    }
}
