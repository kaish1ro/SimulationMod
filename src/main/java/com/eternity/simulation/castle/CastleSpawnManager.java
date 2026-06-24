package com.eternity.simulation.castle;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Спавн мобов из DATA-маркеров (поле {@code mob=...}) по приближению игрока,
 * плюс выпадение ключей {@code simulation:castle_key} с мобов, у которых
 * маркер содержит {@code keyid}.
 *
 * <ul>
 *   <li>группа {@code floor1} (кроме босса) — триггер по приближению на
 *       {@value CastleSpawnDefinition#FLOOR1_TRIGGER_RADIUS} блоков;</li>
 *   <li>{@code floor1_boss} — спавнится автоматически, когда все мобы
 *       группы {@code floor1} мёртвы (не требует приближения игрока);</li>
 *   <li>комнаты/лабиринт — триггер по приближению на
 *       {@value CastleSpawnDefinition#ROOM_TRIGGER_RADIUS} блоков;</li>
 *   <li>при смерти последнего моба точки спавна с {@code keyid} —
 *       выпадает {@code simulation:castle_key{door_id:"<keyid>"}}.</li>
 * </ul>
 */
public final class CastleSpawnManager {

    private CastleSpawnManager() {}

    /** Тег persistentData, по которому смерть моба связывается с точкой спавна. */
    private static final String NBT_SPAWN_INDEX = "simulation_castle_spawn_index";

    /** Тег сущности: мобы замка с этим тегом не наносят урон друг другу (см. {@code ModEvents}). */
    public static final String TEAM_TAG = "simulation_castle_team";

    /** Задержка между появлением дымового столба и самим мобом (тиков). */
    private static final int SPAWN_DELAY_TICKS = 20;

    private static final int TICK_INTERVAL = 20; // раз в секунду
    private static int tickCounter = 0;

    /** Отложенные спавны (дымовой столб → появление моба через {@link #SPAWN_DELAY_TICKS}). */
    private static final List<PendingSpawn> pendingSpawns = new ArrayList<>();

    private static final class PendingSpawn {
        final ServerLevel level;
        final BlockPos spawnPos;
        final CastleSpawnDefinition def;
        final Consumer<LivingEntity> extra;
        int ticksLeft;

        PendingSpawn(ServerLevel level, BlockPos spawnPos, CastleSpawnDefinition def, Consumer<LivingEntity> extra, int ticksLeft) {
            this.level = level;
            this.spawnPos = spawnPos;
            this.def = def;
            this.extra = extra;
            this.ticksLeft = ticksLeft;
        }
    }

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (level == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isCastleSpawnSystemInit()) return;

        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());

        reconcileAliveCounts(level, data, defs);

        for (CastleSpawnDefinition def : defs) {
            if (data.isSpawnTriggered(def.index())) continue;

            if (def.triggerRadius() > 0) {
                if (isPlayerNearby(level, def.pos(), def.triggerRadius())) {
                    spawnGroup(level, data, def);
                }
                continue;
            }

            // floor2 — особый случай: зависит не от группы, а от гибели двух
            // конкретных undead_paladin (по keyid), а не от groupId-группы.
            if ("floor2".equals(def.groupId())) {
                if (keyDoorCleared(defs, data, "outside_tower2") && keyDoorCleared(defs, data, "floor1_yellow_tower")) {
                    spawnGroup(level, data, def);
                }
                continue;
            }

            // triggerRadius <= 0: либо ручной триггер (например blue_tower — пьедесталы,
            // boss_fight — волны босса через CastleBossFightTask),
            // либо группа, ждущая полной гибели группы-предшественницы.
            String dependsOn = dependencyGroup(def);
            if (dependsOn == null) continue;

            if (groupCleared(defs, data, dependsOn)) {
                spawnGroup(level, data, def);
            }
        }
    }

    /**
     * @return groupId группы, после полной гибели которой должна появиться {@code def},
     * или {@code null} если группа триггерится вручную (не зависит от смерти другой группы).
     */
    private static String dependencyGroup(CastleSpawnDefinition def) {
        if (def.isFloor1Boss()) return "floor1";

        String g = def.groupId();

        // "Хранитель тайн" (stray_boss) синей башни спавнится только после полной
        // гибели второй волны последнего этажа синей башни.
        if ("blue_tower_boss".equals(g)) return "id=blue_tower_last_floor_second_wave";

        // Вторая волна последнего этажа синей башни зависит от гибели первой волны
        // той же комнаты (маркер с искажённым groupId "id=outside_blue_towerblue_tower_last_floor").
        if ("id=blue_tower_last_floor_second_wave".equals(g)) return "id=outside_blue_towerblue_tower_last_floor";

        // Волны крыши (roof_wave1/2/3) не имеют авто-зависимости — они только вручную.
        if (g != null && (g.startsWith("roof_wave") || g.startsWith("id=roof_")
                || "roof_second_wave".equals(g))) return null;

        if (g != null && g.endsWith("_second_wave")) {
            return g.substring(0, g.length() - "_second_wave".length());
        }
        return null;
    }

    /** @return true если есть хотя бы одна точка спавна с данным groupId и все они триггернуты и полностью мёртвы. */
    public static boolean groupCleared(List<CastleSpawnDefinition> defs, SimulationSavedData data, String groupId) {
        boolean any = false;
        for (CastleSpawnDefinition d : defs) {
            if (!groupId.equals(d.groupId())) continue;
            any = true;
            if (!data.isSpawnTriggered(d.index()) || data.getSpawnAlive(d.index()) > 0) return false;
        }
        return any;
    }

    /** @return true если есть хотя бы одна точка спавна с данным keyDoorId и все они триггернуты и полностью мёртвы. */
    private static boolean keyDoorCleared(List<CastleSpawnDefinition> defs, SimulationSavedData data, String keyDoorId) {
        boolean any = false;
        for (CastleSpawnDefinition d : defs) {
            if (!keyDoorId.equals(d.keyDoorId())) continue;
            any = true;
            if (!data.isSpawnTriggered(d.index()) || data.getSpawnAlive(d.index()) > 0) return false;
        }
        return any;
    }

    /**
     * Принудительно спавнит все ещё не триггернутые точки спавна с данным groupId —
     * для ручных триггеров (например, группа {@code blue_tower} после решения головоломки).
     */
    public static int triggerGroup(ServerLevel level, SimulationSavedData data, String groupId) {
        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        int triggered = 0;
        for (CastleSpawnDefinition def : defs) {
            if (groupId.equals(def.groupId()) && !data.isSpawnTriggered(def.index())) {
                spawnGroup(level, data, def);
                triggered++;
            }
        }
        return triggered;
    }

    /**
     * Сверяет персистентные счётчики alive с реально существующими мобами.
     *
     * <p>Страховка от «зависших» счётчиков: если моб исчез без LivingDeathEvent
     * (деспавн до фикса setPersistenceRequired, /kill незагруженного, баг другого
     * мода) — счётчик навсегда блокировал бы floor1_boss. Сверяем только точки,
     * рядом с которыми есть игрок (чанки гарантированно загружены и мобы видны).
     */
    private static void reconcileAliveCounts(ServerLevel level, SimulationSavedData data,
                                             List<CastleSpawnDefinition> defs) {
        java.util.Map<Integer, Integer> actual = new java.util.HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            CompoundTag pd = living.getPersistentData();
            if (pd.contains(NBT_SPAWN_INDEX)) {
                actual.merge(pd.getInt(NBT_SPAWN_INDEX), 1, Integer::sum);
            }
        }

        // Учитываем ещё не появившихся мобов (дымовой столб → моб через SPAWN_DELAY_TICKS):
        // иначе сверка обнулит счётчик alive в окно задержки спавна и волна «зачистится» мгновенно.
        for (PendingSpawn pending : pendingSpawns) {
            actual.merge(pending.def.index(), 1, Integer::sum);
        }

        for (CastleSpawnDefinition def : defs) {
            if (!data.isSpawnTriggered(def.index())) continue;

            int recorded = data.getSpawnAlive(def.index());
            if (recorded <= 0) continue;

            // Игрок в 64 блоках — зона вокруг маркера точно загружена.
            if (!isPlayerNearby(level, def.pos(), 64)) continue;

            int found = actual.getOrDefault(def.index(), 0);
            if (found < recorded) {
                data.setSpawnAlive(def.index(), found);
            }
        }
    }

    private static boolean isPlayerNearby(ServerLevel level, BlockPos pos, int radius) {
        double radiusSq = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static void spawnGroup(ServerLevel level, SimulationSavedData data, CastleSpawnDefinition def) {
        data.setSpawnTriggered(def.index());
        spawnMobsRaw(level, def, null);
    }

    /**
     * Спавнит мобов точки спавна без изменения triggered/alive состояния —
     * для повторно спавнящихся волн босса ({@code boss_fight}, через {@code CastleBossFightTask}).
     *
     * <p>Появление каждого моба откладывается на {@link #SPAWN_DELAY_TICKS} — на месте сразу
     * встаёт серый столб дыма (2 блока высотой), который постепенно рассеивается, и только
     * затем появляется сам моб.
     *
     * @param extra доп. донастройка каждой заспавненной {@link LivingEntity} (например, тег волны), может быть {@code null}.
     */
    public static void spawnMobsRaw(ServerLevel level, CastleSpawnDefinition def, Consumer<LivingEntity> extra) {
        EntityType<?> type = CastleMobRegistry.getEntityType(def.mobId());
        if (type == null) return;

        // Маркеры стояли на блок выше пола — опускаем спавн на 1 блок,
        // кроме пауков (king_spider/hedge_spider), которые ставились на полу.
        BlockPos basePos = (def.mobId().equals("king_spider") || def.mobId().equals("hedge_spider"))
                ? def.pos() : def.pos().below();

        for (int i = 0; i < def.count(); i++) {
            BlockPos spawnPos = randomPosInRadius(level, basePos, def.radius() + 1);

            spawnSmokeColumn(level, spawnPos, SPAWN_DELAY_TICKS);
            pendingSpawns.add(new PendingSpawn(level, spawnPos, def, extra, SPAWN_DELAY_TICKS));
        }
    }

    /**
     * Обрабатывает отложенные спавны: каждый игровой тик обновляет дымовой столб
     * и, когда отсчёт доходит до нуля, создаёт моба. Вызывается из {@code ModEvents}
     * каждый тик (без интервала — иначе столб дыма дёргается).
     */
    public static void tickPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;

        Iterator<PendingSpawn> it = pendingSpawns.iterator();
        while (it.hasNext()) {
            PendingSpawn pending = it.next();
            pending.ticksLeft--;

            if (pending.ticksLeft <= 0) {
                doSpawn(pending);
                it.remove();
            } else {
                spawnSmokeColumn(pending.level, pending.spawnPos, pending.ticksLeft);
            }
        }
    }

    /** Создаёт и добавляет в мир моба отложенного спавна. */
    private static void doSpawn(PendingSpawn pending) {
        ServerLevel level = pending.level;
        CastleSpawnDefinition def = pending.def;
        BlockPos spawnPos = pending.spawnPos;

        EntityType<?> type = CastleMobRegistry.getEntityType(def.mobId());
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity == null) return;

        entity.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                level.random.nextFloat() * 360f, 0f);

        // Обычный stray: прогоняем штатный finalizeSpawn, чтобы получить лук
        // и подключить ИИ стрельбы (RangedBowAttackGoal). applyPostSpawn ниже
        // выставит ему 30 HP уже поверх этого.
        if (entity instanceof Mob mob && "stray".equals(def.mobId())) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    net.minecraft.world.entity.MobSpawnType.SPAWNER, null, null);
        }

        if (entity instanceof LivingEntity living) {
            CastleMobRegistry.applyPostSpawn(def.mobId(), living);
            living.getPersistentData().putInt(NBT_SPAWN_INDEX, def.index());
            living.addTag(TEAM_TAG);
            if (pending.extra != null) pending.extra.accept(living);
        }

        // Без этого скелеты деспавнятся при отходе игрока, не вызывая
        // LivingDeathEvent — счётчик alive зависает и блокирует floor1_boss.
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        level.addFreshEntity(entity);
    }

    /**
     * Столб серого дыма (2 блока высотой) на месте будущего спавна моба —
     * плотность падает вместе с {@code ticksLeft}, создавая эффект рассеивания.
     */
    private static void spawnSmokeColumn(ServerLevel level, BlockPos pos, int ticksLeft) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        int count = Math.max(1, ticksLeft);
        for (int i = 0; i < count; i++) {
            double y = pos.getY() + level.random.nextDouble() * 2.0;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, y, cz, 1, 0.25, 0.0, 0.25, 0.01);
        }
    }

    private static BlockPos randomPosInRadius(ServerLevel level, BlockPos center, int radius) {
        if (radius <= 0) return center;
        int dx = level.random.nextInt(radius * 2 + 1) - radius;
        int dz = level.random.nextInt(radius * 2 + 1) - radius;
        return center.offset(dx, 0, dz);
    }

    /** Дроп {@code castle_key} при смерти последнего моба точки спавна с {@code keyid}. */
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag persistentData = entity.getPersistentData();
        if (!persistentData.contains(NBT_SPAWN_INDEX)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        int index = persistentData.getInt(NBT_SPAWN_INDEX);
        SimulationSavedData data = SimulationSavedData.get(level.getServer().overworld());

        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        if (index < 0 || index >= defs.size()) return;
        CastleSpawnDefinition def = defs.get(index);

        int remaining = data.decrementSpawnAlive(index);
        if (remaining == 0 && def.keyDoorId() != null) {
            ItemStack key = new ItemStack(ModItems.CASTLE_KEY.get());
            CompoundTag tag = new CompoundTag();
            tag.putString("door_id", def.keyDoorId());
            key.setTag(tag);

            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), key));
        }
    }
}
