package com.eternity.simulation.castle;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Финальная битва с главным боссом 2-го этажа ({@code block_factorys_bosses:underworld_knight}).
 *
 * <p>Стадии (см. {@link SimulationSavedData#getBossFightStage()}):
 * <ol start="0">
 *   <li>ждём гибели группы {@code floor2};</li>
 *   <li>вступление — драматичное сообщение, 10 молний и спавн босса на {@code Boss_spawn.below()};</li>
 *   <li>ждём падения HP босса до 25% — затем спавним волну 1 ({@code id=boss_fight}) и делаем босса неуязвимым;</li>
 *   <li>волна 1 активна — ждём смерти всех её мобов;</li>
 *   <li>ждём, пока босс сам не вылечится до 100% (встроенный переход во 2-ю фазу
 *       {@code underworld_knight});</li>
 *   <li>ждём падения HP до 50% (отсчёт от полного HP после самоисцеления) — затем волна 2, неуязвимость;</li>
 *   <li>волна 2 активна — ждём смерти всех её мобов;</li>
 *   <li>финал — ждём смерти босса, затем спавним сундук с ключом {@code castle_roof}, маяком и {@code boss_loot}.</li>
 * </ol>
 */
public final class CastleBossFightTask {

    private static final Logger LOGGER = LogManager.getLogger("simulation.CastleBossFight");

    private static final int TICK_INTERVAL = 10;
    private static int tickCounter = 0;

    private static final String WAVE_TAG = "simulation_boss_wave";

    /** Тег главного босса — по нему {@code ModEvents} запрещает ему ломать блоки (лестницы и т.п.). */
    public static final String MAIN_BOSS_TAG = "simulation_castle_main_boss";

    /** Доля макс. HP, до которой босс продавливается перед спавном 1-й/2-й волны. */
    private static final double WAVE1_THRESHOLD = 0.25;
    private static final double WAVE2_THRESHOLD = 0.50;

    /** Ценные блоки, которыми заполняется сундук с лутом босса (вместо лут-таблицы). */
    private static final String[] LOOT_BLOCK_IDS = {
        "minecraft:netherite_block",
        "minecraft:diamond_block",
        "minecraft:emerald_block",
        "minecraft:gold_block",
        "minecraft:iron_block",
        "minecraft:lapis_block",
        "minecraft:redstone_block",
    };

    /** Сколько вызовов {@link #tick()} (по {@link #TICK_INTERVAL} тиков) длится эффект
     *  фиолетовых партиклов перед появлением сундука с лутом (~3.5 сек). */
    private static final int LOOT_PARTICLE_STEPS = 7;

    /** Позиция сундука с лутом, ожидающего появления после эффекта партиклов, или {@code null}. */
    private static BlockPos pendingLootPos;
    private static int pendingLootSteps;

    private CastleBossFightTask() {}

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (level == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isCastleSpawnSystemInit()) return;

        if (pendingLootPos != null) {
            spawnLootParticles(level, pendingLootPos);
            if (--pendingLootSteps <= 0) {
                BlockPos pos = pendingLootPos;
                pendingLootPos = null;
                placeBossLootChest(level, pos);
            }
            return;
        }

        switch (data.getBossFightStage()) {
            case 0 -> tryStartFight(level, data);
            case 2 -> watchHealthThreshold(level, data, WAVE1_THRESHOLD, 1);
            case 3 -> watchWaveCleared(level, data, 1);
            case 4 -> watchSelfHeal(level, data);
            case 5 -> watchHealthThreshold(level, data, WAVE2_THRESHOLD, 2);
            case 6 -> watchWaveCleared(level, data, 2);
            case 7 -> watchBossDeath(level, data);
            default -> { /* 1 и 8 — переходные/финальные, обрабатываются синхронно */ }
        }
    }

    /**
     * Обработка урона по боссу (вызывается из {@code ModEvents} на {@link LivingHurtEvent}).
     *
     * <p>Надёжная замена {@code setInvulnerable} (которую кастомный {@code underworld_knight}
     * игнорирует в своём переопределённом {@code hurt}):
     * <ul>
     *   <li>стадии 3/6 (активна волна) — урон полностью отменяется (неуязвимость);</li>
     *   <li>стадии 2/5 (ждём порог) — урон обрезается так, чтобы HP не упал ниже порога
     *       (25% / 50%), иначе сильный игрок «проскакивает» порог за один тик и волна
     *       не успевает заспавниться.</li>
     * </ul>
     */
    public static void onBossHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        UUID bossUuid = data.getBossEntityUuid();
        if (bossUuid == null) return;

        LivingEntity boss = event.getEntity();
        if (!boss.getUUID().equals(bossUuid)) return;

        int stage = data.getBossFightStage();
        if (stage == 3 || stage == 6) {
            event.setCanceled(true); // неуязвимость, пока жива волна
            return;
        }

        double fraction;
        if (stage == 2) fraction = WAVE1_THRESHOLD;
        else if (stage == 5) fraction = WAVE2_THRESHOLD;
        else return;

        float floor = (float) (boss.getMaxHealth() * fraction);
        float healthAfter = boss.getHealth() - event.getAmount();
        if (healthAfter < floor) {
            event.setAmount(Math.max(0f, boss.getHealth() - floor));
        }
    }

    /**
     * Сбрасывает битву с боссом в начальное состояние (стадия 0) для повторного
     * тестирования без пересоздания мира: убирает текущего босса (если жив) и
     * всех мобов уже заспавненных волн. Группа {@code floor2} не трогается —
     * если она уже была зачищена ранее, бой запустится заново автоматически.
     */
    public static void resetBossFight(ServerLevel level, SimulationSavedData data) {
        UUID bossUuid = data.getBossEntityUuid();
        if (bossUuid != null) {
            Entity boss = level.getEntity(bossUuid);
            if (boss != null) boss.discard();
        }

        List<Entity> waveMobs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive() && entity.getPersistentData().contains(WAVE_TAG)) {
                waveMobs.add(entity);
            }
        }
        waveMobs.forEach(Entity::discard);

        data.setBossFightStage(0);
        data.setBossEntityUuid(null);
    }

    /** Стадия 0: ждём, пока вся группа {@code floor2} не умрёт. */
    private static void tryStartFight(ServerLevel level, SimulationSavedData data) {
        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        if (!CastleSpawnManager.groupCleared(defs, data, "floor2")) return;

        BlockPos bossPos = findMarkerPos(data, "boss_spawn");
        if (bossPos == null) return;

        spawnBoss(level, data, bossPos);
        if (data.getBossEntityUuid() == null) return;

        announceBossIntro(level);
        strikeLightning(level, bossPos, 10);

        data.setBossFightStage(2);
    }

    private static void announceBossIntro(ServerLevel level) {
        Component message = Component.literal(
            "§4§l☠ Довольно! §c§lВы испытали моё терпение в последний раз — теперь замок утолит свою жажду вашей кровью!");
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void strikeLightning(ServerLevel level, BlockPos pos, int count) {
        if (pos == null) return;
        for (int i = 0; i < count; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;

            double dx = (level.random.nextDouble() - 0.5) * 4.0;
            double dz = (level.random.nextDouble() - 0.5) * 4.0;
            bolt.moveTo(pos.getX() + 0.5 + dx, pos.getY(), pos.getZ() + 0.5 + dz);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private static void spawnBoss(ServerLevel level, SimulationSavedData data, BlockPos pos) {
        EntityType<?> type = CastleMobRegistry.getEntityType("underworld_knight_boss");
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity == null) return;

        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);

        if (entity instanceof LivingEntity living) {
            CastleMobRegistry.applyPostSpawn("underworld_knight_boss", living);
        }

        // Главный босс не наносит урон своим приспешникам и не ломает блоки (лестницы и т.п.).
        entity.addTag(CastleSpawnManager.TEAM_TAG);
        entity.addTag(MAIN_BOSS_TAG);

        level.addFreshEntity(entity);
        data.setBossEntityUuid(entity.getUUID());
    }

    /** Стадии 2/5: ждём, пока HP босса упадёт до заданной доли максимума, затем спавним волну и блокируем урон. */
    private static void watchHealthThreshold(ServerLevel level, SimulationSavedData data, double fraction, int wave) {
        LivingEntity boss = getBoss(level, data);
        if (boss == null) {
            onBossDeadEarly(level, data);
            return;
        }

        if (boss.getHealth() > boss.getMaxHealth() * fraction) return;

        // Неуязвимость на время волны обеспечивает onBossHurt (стадии 3/6).
        spawnWave(level, data, wave);

        data.setBossFightStage(wave == 1 ? 3 : 6);
    }

    /** Стадии 3/6: ждём смерти всех мобов текущей волны. Неуязвимость снимается автоматически (onBossHurt). */
    private static void watchWaveCleared(ServerLevel level, SimulationSavedData data, int wave) {
        if (countAliveWaveMobs(level, wave) > 0) return;

        if (getBoss(level, data) == null) {
            onBossDeadEarly(level, data);
            return;
        }

        data.setBossFightStage(wave == 1 ? 4 : 7);
    }

    /**
     * Стадия 4: ждём, пока босс не вылечится сам до 100% HP — это встроенный переход
     * {@code underworld_knight} во 2-ю фазу. От этого момента отсчитываем порог волны 2.
     */
    private static void watchSelfHeal(ServerLevel level, SimulationSavedData data) {
        LivingEntity boss = getBoss(level, data);
        if (boss == null) {
            onBossDeadEarly(level, data);
            return;
        }

        if (boss.getHealth() < boss.getMaxHealth()) return;

        data.setBossFightStage(5);
    }

    /** Стадия 7: финал — ждём смерти босса, затем выдаём награду. */
    private static void watchBossDeath(ServerLevel level, SimulationSavedData data) {
        if (getBoss(level, data) != null) return;
        onBossDefeated(level, data);
    }

    /** Босс умер раньше, чем должна была появиться очередная волна — всё равно выдаём награду. */
    private static void onBossDeadEarly(ServerLevel level, SimulationSavedData data) {
        onBossDefeated(level, data);
    }

    /**
     * Босс побеждён: если есть маркер {@code set_chest}, запускаем эффект фиолетовых
     * партиклов на {@link #LOOT_PARTICLE_STEPS} вызовов {@link #tick()}, по завершении
     * которого появляется сундук с лутом (см. {@link #placeBossLootChest}).
     */
    private static void onBossDefeated(ServerLevel level, SimulationSavedData data) {
        data.setBossFightStage(8);
        data.setBossEntityUuid(null);

        // Хелвар (underworld_knight_boss) — квест "Войдите в жёлтые башни" завершён
        CastleSubQuestTask.onHelvarDefeated(level.getServer());

        // Размещаем крышу замка автоматически при победе над боссом
        if (data.hasCastleAnchor()) {
            LOGGER.info("[BossFight] Запускаем CastlePlacementTask.runRoof после победы над боссом");
            CastlePlacementTask.runRoof(level, data.getCastleAnchorPos(), null);
        } else {
            LOGGER.warn("[BossFight] Якорь замка не захвачен — крыша не поставлена");
        }

        BlockPos chestPos = findMarkerPos(data, "set_chest");
        if (chestPos != null) {
            pendingLootPos = chestPos;
            pendingLootSteps = LOOT_PARTICLE_STEPS;
        }
    }

    private static void spawnWave(ServerLevel level, SimulationSavedData data, int wave) {
        announceWave(level, wave);

        List<CastleSpawnDefinition> defs = CastleSpawnDefinition.fromMarkers(data.getCastleMarkers());
        for (CastleSpawnDefinition def : defs) {
            if (!"boss_fight".equals(def.groupId())) continue;
            CastleSpawnManager.spawnMobsRaw(level, def,
                living -> living.getPersistentData().putInt(WAVE_TAG, wave));
        }
    }

    /** Оповещение от лица босса о появлении очередной волны приспешников. */
    private static void announceWave(ServerLevel level, int wave) {
        String text = wave == 1
            ? "§4§l☠ §c§lВосстаньте, верные клинки тьмы — разорвите этих наглецов на куски!"
            : "§4§l☠ §c§lЕщё не время праздновать! Поднимайтесь снова, тени — клятва ещё в силе!";
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private static int countAliveWaveMobs(ServerLevel level, int wave) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            CompoundTag pd = living.getPersistentData();
            if (pd.contains(WAVE_TAG) && pd.getInt(WAVE_TAG) == wave) {
                count++;
            }
        }
        return count;
    }

    private static LivingEntity getBoss(ServerLevel level, SimulationSavedData data) {
        UUID uuid = data.getBossEntityUuid();
        if (uuid == null) return null;
        Entity entity = level.getEntity(uuid);
        return (entity instanceof LivingEntity living && living.isAlive()) ? living : null;
    }

    /** Облако фиолетовых партиклов на месте будущего сундука с лутом. */
    private static void spawnLootParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz, 40, 0.5, 0.5, 0.5, 0.3);
        level.sendParticles(ParticleTypes.WITCH, cx, cy, cz, 10, 0.4, 0.4, 0.4, 0.05);
    }

    /**
     * Ставит призмариновый сундук (quark), повёрнутый на запад, на месте маркера {@code set_chest}:
     * в среднем слоте — {@code castle_key{door_id:"castle_roof"}}, в слоте под ним — маяк,
     * остальные слоты — случайные ценные блоки ({@link #LOOT_BLOCK_IDS}).
     */
    private static void placeBossLootChest(ServerLevel level, BlockPos pos) {
        Block chestBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("quark", "prismarine_chest"));
        if (chestBlock == null) return;
        level.setBlock(pos, facingWest(chestBlock), Block.UPDATE_CLIENTS);

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity chest)) return;

        int size = chest.getContainerSize();
        int middleSlot = size / 2;
        int beaconSlot = middleSlot + 9;

        for (int slot = 0; slot < size; slot++) {
            if (slot == middleSlot || slot == beaconSlot) continue;
            ItemStack lootBlock = randomLootBlock(level);
            if (!lootBlock.isEmpty()) chest.setItem(slot, lootBlock);
        }

        CompoundTag keyTag = new CompoundTag();
        keyTag.putString("door_id", "castle_roof");
        ItemStack key = new ItemStack(ModItems.CASTLE_KEY.get());
        key.setTag(keyTag);
        chest.setItem(middleSlot, key);

        if (beaconSlot < size) {
            chest.setItem(beaconSlot, new ItemStack(Items.BEACON));
        }

        chest.setChanged();
    }

    /** @return стак из 1-4 случайных ценных блоков ({@link #LOOT_BLOCK_IDS}). */
    private static ItemStack randomLootBlock(ServerLevel level) {
        ResourceLocation id = new ResourceLocation(LOOT_BLOCK_IDS[level.random.nextInt(LOOT_BLOCK_IDS.length)]);
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) return ItemStack.EMPTY;
        return new ItemStack(block, 1 + level.random.nextInt(4));
    }

    private static BlockState facingWest(Block chestBlock) {
        BlockState state = chestBlock.defaultBlockState();
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp && dirProp.getPossibleValues().contains(Direction.WEST)) {
                state = state.setValue(dirProp, Direction.WEST);
            }
        }
        return state;
    }

    private static BlockPos findMarkerPos(SimulationSavedData data, String key) {
        for (CastleDataMarker marker : data.getCastleMarkers()) {
            if (marker.has(key)) return marker.pos();
        }
        return null;
    }
}
