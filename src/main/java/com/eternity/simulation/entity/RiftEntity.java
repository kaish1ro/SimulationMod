package com.eternity.simulation.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Разлом — огромная вертикальная трещина в небе.
 *
 * <p>Четыре типа ({@link RiftType}), каждый со своим пулом мобов и текстурой.
 * Элитный разлом ({@code ELITE}) вдвое больше обычного.
 *
 * <p>Конечный автомат: IDLE → ACTIVE → CLOSING → despawn
 */
public class RiftEntity extends Entity {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String MOD_ID = "threateningly_mobs";

    // ── Synched Data ──────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Byte>  DATA_STATE    =
        SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_FIXED_YAW =
        SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CLOSING_PROGRESS =
        SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte>  DATA_TYPE     =
        SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.BYTE);

    // ── Тип разлома ───────────────────────────────────────────────────────────

    /**
     * Тип разлома определяет текстуру, пул мобов и размер.
     *
     * <p>HP-суммы пулов (сумма базового здоровья всех 6 видов):
     * <ul>
     *   <li>RED    — 197 HP</li>
     *   <li>PURPLE — 294 HP</li>
     *   <li>BLUE   — 190 HP</li>
     *   <li>ELITE  — 100 HP (один вид, 2× размер разлома)</li>
     * </ul>
     */
    public enum RiftType {
        /**
         * Красная (огненная). Мобы: Flamelarva, Fire Lizard, Sandworm,
         * Forest Drake, Tall Mouse, Elite Zombie Warrior. HP пула: 197.
         * 50 мобов → ~1642 суммарного HP.
         */
        RED((byte) 0, "rift1", new String[]{
            "flamelarva", "fire_lizard", "sandworm",
            "forest_drake", "tall_mouse", "elite_zombie_warrior"
        }, 1f, 50),

        /**
         * Фиолетовая (тёмная). Мобы: Nibbler, Devourer, Snow Servent,
         * Castylosaurus, Cannon Crab, Ore Drill. HP пула: 294.
         * 33 моба → ~1617 суммарного HP.
         */
        PURPLE((byte) 1, "rift2", new String[]{
            "nibbler", "devourer", "snow_servent",
            "dracoscorpius", "cannon_crab", "ore_drill"
        }, 1f, 33),

        /**
         * Голубая (ледяная). Мобы: Mega Aphid, Ice Weaver, Beast Horseshoe Crab,
         * Tide Specter, Cave Hunter, Hydra Cub. HP пула: 190.
         * 52 моба → ~1647 суммарного HP.
         */
        BLUE((byte) 2, "rift3", new String[]{
            "mega_aphid", "ice_weaver", "beast_horsehoe_crab",
            "tide_specter", "cave_hunter", "hydra_cub"
        }, 1f, 52),

        /**
         * Элитная. Моб: Skeleton Predator (100 HP).
         * 16 мобов → ~1600 суммарного HP. Разлом вдвое больше остальных.
         */
        ELITE((byte) 3, "rift4", new String[]{
            "skeleton_predator"
        }, 2f, 16);

        public  final byte     id;
        /** Имя файла текстуры без расширения (под assets/simulation/textures/entity/). */
        public  final String   textureName;
        /** Возможные мобы для случайного выбора при спавне волны. */
        public  final String[] mobPool;
        /** Множитель размера (1.0 = стандартный 50×5, 2.0 = 100×10). */
        public  final float    sizeScale;
        /** Суммарное количество мобов за весь разлом (~1600 суммарного HP). */
        public  final int      totalMobs;

        RiftType(byte id, String textureName, String[] mobPool, float sizeScale, int totalMobs) {
            this.id          = id;
            this.textureName = textureName;
            this.mobPool     = mobPool;
            this.sizeScale   = sizeScale;
            this.totalMobs   = totalMobs;
        }

        public static RiftType fromId(byte id) {
            for (RiftType t : values()) if (t.id == id) return t;
            return RED;
        }
    }

    // ── Состояния ─────────────────────────────────────────────────────────────

    public enum State {
        IDLE   ((byte) 0),
        ACTIVE ((byte) 1),
        CLOSING((byte) 2);

        public final byte id;
        State(byte id) { this.id = id; }

        public static State fromId(byte id) {
            for (State s : values()) if (s.id == id) return s;
            return IDLE;
        }
    }

    // ── Базовые размеры (до применения sizeScale) ─────────────────────────────

    public static final float BASE_LENGTH = 50f;
    public static final float BASE_HEIGHT = 5f;

    // ── Константы ─────────────────────────────────────────────────────────────

    public static final int ACTIVATION_RANGE  = 150;
    public static final int CLOSING_DURATION  = 200;

    private static final int SPAWN_COOLDOWN_MIN = 400;   // 20 секунд
    private static final int SPAWN_COOLDOWN_MAX = 480;   // 24 секунды
    private static final int BATCH_MIN          = 3;
    private static final int BATCH_MAX          = 6;

    // ── Серверное состояние ───────────────────────────────────────────────────

    private final Set<UUID> activeMobs   = new HashSet<>();
    private int totalSpawned  = 0;
    private int spawnCooldown = SPAWN_COOLDOWN_MIN;
    private int closingTicks  = 0;

    // ── Конструктор ───────────────────────────────────────────────────────────

    public RiftEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    // ── Synched Data ──────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_STATE,            State.IDLE.id);
        entityData.define(DATA_FIXED_YAW,        0f);
        entityData.define(DATA_CLOSING_PROGRESS, 0f);
        entityData.define(DATA_TYPE,             RiftType.RED.id);
    }

    public State    getState()            { return State.fromId(entityData.get(DATA_STATE)); }
    private void    setState(State s)     { entityData.set(DATA_STATE, s.id); }

    public float    getFixedYaw()         { return entityData.get(DATA_FIXED_YAW); }
    public void     setFixedYaw(float y)  { entityData.set(DATA_FIXED_YAW, y); }

    public float    getClosingProgress()  { return entityData.get(DATA_CLOSING_PROGRESS); }

    public RiftType getRiftType()               { return RiftType.fromId(entityData.get(DATA_TYPE)); }
    public void     setRiftType(RiftType t)     { entityData.set(DATA_TYPE, t.id); }

    /** Длина разлома с учётом типа (50 или 100 блоков). */
    public float getLength()   { return BASE_LENGTH * getRiftType().sizeScale; }
    /** Высота разлома с учётом типа. */
    public float getHeight()   { return BASE_HEIGHT * getRiftType().sizeScale; }

    /** Принудительно переводит разлом в состояние закрытия (без счётчиков прогрессии). */
    public void forceClose() {
        entityData.set(DATA_STATE, State.CLOSING.id);
    }

    // ── Тик ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) tickClient();
        else                        tickServer();
    }

    private void tickServer() {
        if (!(level() instanceof ServerLevel sl)) return;

        switch (getState()) {

            case IDLE -> {
                Player nearest = sl.getNearestPlayer(this, ACTIVATION_RANGE);
                if (nearest != null) {
                    setState(State.ACTIVE);
                    spawnCooldown = 40;
                    playSpawnSounds(sl);
                    LOGGER.info("[RiftEntity][{}] Activated near '{}'",
                            getRiftType(), nearest.getName().getString());
                }
            }

            case ACTIVE -> {
                activeMobs.removeIf(uuid -> {
                    Entity e = sl.getEntity(uuid);
                    return e == null || !e.isAlive();
                });

                int totalMobs = getRiftType().totalMobs;
                if (totalSpawned < totalMobs && --spawnCooldown <= 0) {
                    spawnBatch(sl);
                    spawnCooldown = SPAWN_COOLDOWN_MIN
                            + sl.random.nextInt(SPAWN_COOLDOWN_MAX - SPAWN_COOLDOWN_MIN);
                }

                // Фоновые звуки пока разлом открыт (напрямую игрокам — без затухания)
                if (this.tickCount % 100 == 0) {
                    playDirect(sl, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 0.7f);
                }
                if (this.tickCount % 400 == 0 && this.tickCount > 0) {
                    playDirect(sl, net.minecraft.sounds.SoundEvents.WARDEN_AMBIENT,
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.3f, 0.8f);
                }

                if (totalSpawned >= totalMobs && activeMobs.isEmpty()) {
                    setState(State.CLOSING);
                    playClosingSounds(sl);
                    LOGGER.info("[RiftEntity][{}] All mobs slain, closing", getRiftType());
                }
            }

            case CLOSING -> {
                float progress = Math.min(1f, ++closingTicks / (float) CLOSING_DURATION);
                entityData.set(DATA_CLOSING_PROGRESS, progress);
                if (progress >= 1f) {
                    RiftManager.INSTANCE.onRiftClosed(this.getUUID(), this.getRiftType());
                    this.discard();
                }
            }
        }
    }

    // ── Звуки ─────────────────────────────────────────────────────────────────

    /** Радиус (блоки) в котором игроки слышат звуки разлома напрямую. */
    private static final double SOUND_RANGE_SQ = 300.0 * 300.0;

    /**
     * Отправляет звук напрямую каждому игроку в радиусе {@code SOUND_RANGE_SQ}.
     * Не зависит от позиции разлома — нет дистанционного затухания.
     */
    private void playDirect(ServerLevel sl,
                             net.minecraft.sounds.SoundEvent sound,
                             net.minecraft.sounds.SoundSource source,
                             float volume, float pitch) {
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) <= SOUND_RANGE_SQ) {
                p.playNotifySound(sound, source, volume, pitch);
            }
        }
    }

    private void playSpawnSounds(ServerLevel sl) {
        // Слой 1 — низкий гул (напрямую → не режется расстоянием)
        playDirect(sl, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.4f, 0.3f);
        // Слой 2 — разрыв ткани реальности
        playDirect(sl, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.3f, 1.8f);
        // Слой 3 — финальный "хлопок"
        playDirect(sl, net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.8f, 1.0f);
    }

    private void playClosingSounds(ServerLevel sl) {
        // Схлопывание
        playDirect(sl, net.minecraft.sounds.SoundEvents.WARDEN_DEATH,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.6f, 0.8f);
        // Финальный щелчок
        playDirect(sl, net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.3f, 0.5f);
    }

    // ── Спавн волны ───────────────────────────────────────────────────────────

    private void spawnBatch(ServerLevel level) {
        int base    = BATCH_MIN + level.random.nextInt(BATCH_MAX - BATCH_MIN + 1);
        int players = Math.max(1, level.players().size());
        // Умножаем на 1.5 за каждого игрока: 1→base, 2→base×1.5, 3→base×2.25 …
        int count   = (int)(base * Math.pow(1.5, players - 1));
        count = Math.min(count, getRiftType().totalMobs - totalSpawned);

        double yawRad = Math.toRadians(getFixedYaw());
        double dirX   = Math.cos(yawRad);
        double dirZ   = Math.sin(yawRad);
        float  halfLen = getLength() / 2f;

        String[] pool = getRiftType().mobPool;

        for (int i = 0; i < count; i++) {
            double t = (level.random.nextDouble() - 0.5) * halfLen * 2;
            double x = getX() + t * dirX + (level.random.nextDouble() - 0.5) * 3;
            double y = getY() - 1.5;
            double z = getZ() + t * dirZ + (level.random.nextDouble() - 0.5) * 3;

            String mobId = pool[level.random.nextInt(pool.length)];
            Mob mob = createModMob(level, mobId);
            if (mob == null) continue;

            mob.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
            mob.addTag("rift_mob");
            mob.addTag("rift_type_" + getRiftType().name().toLowerCase());
            mob.finalizeSpawn(level,
                    level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null, null);
            // Запрещаем деспавн — моб обязан быть убит игроком, не исчезнуть сам
            mob.setPersistenceRequired();

            if (level.addFreshEntity(mob)) {
                activeMobs.add(mob.getUUID());
                totalSpawned++;
            }
        }

        LOGGER.debug("[RiftEntity][{}] Batch done, total {}/{}",
                getRiftType(), totalSpawned, getRiftType().totalMobs);
    }

    @Nullable
    private static Mob createModMob(ServerLevel level, String name) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation(MOD_ID, name));
        if (type == null) {
            LOGGER.warn("[RiftEntity] Unknown entity type: {}:{}", MOD_ID, name);
            return null;
        }
        Entity entity = type.create(level);
        return entity instanceof Mob m ? m : null;
    }

    // ── Клиентские частицы ───────────────────────────────────────────────────

    private void tickClient() {
        State state = getState();
        if (state == State.IDLE) return;

        RandomSource rng    = level().random;
        float closingProgress = getClosingProgress();
        float halfLen = (getLength() / 2f) * (1f - closingProgress);

        double yawRad = Math.toRadians(getFixedYaw());
        double dirX   = Math.cos(yawRad);
        double dirZ   = Math.sin(yawRad);

        int count = state == State.CLOSING ? 10 : 5;
        for (int i = 0; i < count; i++) {
            double t  = (rng.nextDouble() - 0.5) * 2 * halfLen;
            double px = getX() + t * dirX + (rng.nextDouble() - 0.5) * 1.5;
            double py = getY() + (rng.nextDouble() - 0.5) * getHeight();
            double pz = getZ() + t * dirZ + (rng.nextDouble() - 0.5) * 1.5;
            level().addParticle(ParticleTypes.PORTAL, px, py, pz,
                    (rng.nextDouble() - 0.5) * 0.3,
                    (rng.nextDouble() - 0.5) * 0.3,
                    (rng.nextDouble() - 0.5) * 0.3);
        }

        if (rng.nextFloat() < 0.4f) {
            double t        = (rng.nextDouble() - 0.5) * 2 * halfLen;
            double edgeSign = rng.nextBoolean() ? 1 : -1;
            level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    getX() + t * dirX, getY() + edgeSign * getHeight() * 0.5, getZ() + t * dirZ,
                    (rng.nextDouble() - 0.5) * 0.2, edgeSign * 0.15, (rng.nextDouble() - 0.5) * 0.2);
        }

        if (state == State.CLOSING && rng.nextFloat() < 0.5f) {
            double side = rng.nextBoolean() ? halfLen : -halfLen;
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    getX() + side * dirX, getY() + (rng.nextDouble() - 0.5) * getHeight(), getZ() + side * dirZ,
                    -side * dirX * 0.05, 0, -side * dirZ * 0.05);
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_STATE,            tag.getByte("riftState"));
        entityData.set(DATA_FIXED_YAW,        tag.getFloat("fixedYaw"));
        entityData.set(DATA_CLOSING_PROGRESS, tag.getFloat("closingProgress"));
        entityData.set(DATA_TYPE,             tag.getByte("riftType"));
        totalSpawned  = tag.getInt("totalSpawned");
        spawnCooldown = tag.getInt("spawnCooldown");
        closingTicks  = tag.getInt("closingTicks");

        activeMobs.clear();
        ListTag list = tag.getList("activeMobs", 8);
        for (int i = 0; i < list.size(); i++) {
            try { activeMobs.add(UUID.fromString(list.getString(i))); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("riftState",        entityData.get(DATA_STATE));
        tag.putFloat("fixedYaw",        entityData.get(DATA_FIXED_YAW));
        tag.putFloat("closingProgress", entityData.get(DATA_CLOSING_PROGRESS));
        tag.putByte("riftType",         entityData.get(DATA_TYPE));
        tag.putInt("totalSpawned",      totalSpawned);
        tag.putInt("spawnCooldown",     spawnCooldown);
        tag.putInt("closingTicks",      closingTicks);

        ListTag list = new ListTag();
        for (UUID uuid : activeMobs) list.add(StringTag.valueOf(uuid.toString()));
        tag.put("activeMobs", list);
    }

    // ── Разное ────────────────────────────────────────────────────────────────

    @Override public boolean isPickable()    { return false; }
    @Override public boolean shouldBeSaved() { return true; }
}
