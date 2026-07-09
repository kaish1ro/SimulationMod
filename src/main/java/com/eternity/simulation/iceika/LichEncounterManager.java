package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Раскладка башни фиксированная — маркеры {@code second_floor}/{@code
 * third_floor}/{@code lich_spawn} были в NBT-файле (см. историю правки:
 * все три {@code minecraft:structure_block} в режиме DATA заменены на лёд
 * прямо в файле, поэтому их относительные позиции просто зашиты здесь
 * константами, обнаружить их после размещения уже нельзя).
 *
 * <p>2 и 3 этаж — по 4-5 бледных лучников (divinerpg:pale_archer) вокруг
 * соответствующей точки. Лич (bosses_of_mass_destruction:lich) появляется
 * в lich_spawn только после того, как ВСЕ лучники обеих групп мертвы —
 * отслеживается через {@link LichEncounterRegistry}, проверяется тиком.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class LichEncounterManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceKey<Level> ICEIKA_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("divinerpg", "iceika"));

    private static final BlockPos SECOND_FLOOR_OFFSET = new BlockPos(12, 17, 15);
    private static final BlockPos THIRD_FLOOR_OFFSET = new BlockPos(12, 30, 15);
    private static final BlockPos LICH_SPAWN_OFFSET = new BlockPos(12, 45, 15);

    private static final ResourceLocation PALE_ARCHER_ID = new ResourceLocation("divinerpg", "pale_archer");
    private static final ResourceLocation LICH_ID = new ResourceLocation("bosses_of_mass_destruction", "lich");

    private static final int MIN_ARCHERS_PER_FLOOR = 4;
    private static final int MAX_ARCHERS_PER_FLOOR = 5;
    private static final int ARCHER_SCATTER_RADIUS = 2;
    private static final int PLACEMENT_ATTEMPTS_PER_ARCHER = 6;

    private static final int CHECK_INTERVAL = 20;

    /** Вызывается сразу после установки шаблона башни — заселяет её лучниками и заводит встречу. */
    public static void startEncounter(ServerLevel level, BlockPos towerOrigin) {
        RandomSource random = level.getRandom();
        List<UUID> archers = new ArrayList<>();
        archers.addAll(spawnArchers(level, towerOrigin.offset(SECOND_FLOOR_OFFSET), random));
        archers.addAll(spawnArchers(level, towerOrigin.offset(THIRD_FLOOR_OFFSET), random));

        BlockPos lichSpawnPos = towerOrigin.offset(LICH_SPAWN_OFFSET);
        LichEncounterRegistry.get(level).add(new LichEncounterRegistry.PendingEncounter(lichSpawnPos, archers));
    }

    private static List<UUID> spawnArchers(ServerLevel level, BlockPos floorMarker, RandomSource random) {
        EntityType<?> archerType = ForgeRegistries.ENTITY_TYPES.getValue(PALE_ARCHER_ID);
        List<UUID> ids = new ArrayList<>();
        if (archerType == null) {
            LOGGER.warn("[simulation] divinerpg:pale_archer не найден в реестре — DivineRPG не установлен?");
            return ids;
        }

        int count = MIN_ARCHERS_PER_FLOOR + random.nextInt(MAX_ARCHERS_PER_FLOOR - MIN_ARCHERS_PER_FLOOR + 1);
        for (int i = 0; i < count; i++) {
            BlockPos spot = findClearSpot(level, floorMarker, random);

            Entity entity = archerType.create(level);
            if (entity == null) continue;
            entity.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (entity instanceof Mob mob) {
                // Лучники спавнятся при появлении башни — то есть обычно ДАЛЕКО от
                // игрока. Без persistence они как MONSTER-мобы деспавнятся по
                // обычным правилам ещё до того, как игрок доберётся до башни.
                // Та же проблема была со стражами замка.
                mob.setPersistenceRequired();
                // EntityPaleArcher вешает лук (divinerpg:icicle_bow) в
                // populateDefaultEquipment(), который вызывается из
                // finalizeSpawn() — а не автоматически при EntityType.create().
                // Без этого вызова лучники спавнятся безоружными.
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
            }
            level.addFreshEntity(entity);
            ids.add(entity.getUUID());
        }
        LOGGER.info("[simulation] lich_tower: заспавнено {} из {} лучников у маркера {}", ids.size(), count, floorMarker);
        return ids;
    }

    /**
     * Маркер — фиксированная точка, зашитая в код (см. класс-javadoc), а не
     * реально прочитанная из размещённой структуры — если из-за неровного
     * рельефа/иной геометрии эта точка окажется внутри стены, лучник задохнётся
     * почти мгновенно. Проверяем пару соседних клеток вокруг на отсутствие
     * коллизии, прежде чем ставить туда сущность.
     */
    private static BlockPos findClearSpot(ServerLevel level, BlockPos floorMarker, RandomSource random) {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS_PER_ARCHER; attempt++) {
            int dx = attempt == 0 ? 0 : random.nextInt(ARCHER_SCATTER_RADIUS * 2 + 1) - ARCHER_SCATTER_RADIUS;
            int dz = attempt == 0 ? 0 : random.nextInt(ARCHER_SCATTER_RADIUS * 2 + 1) - ARCHER_SCATTER_RADIUS;
            BlockPos candidate = floorMarker.offset(dx, 0, dz);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
                return candidate;
            }
        }
        return floorMarker; // не нашли ничего лучше — хотя бы сам маркер
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ICEIKA_KEY)) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;

        LichEncounterRegistry registry = LichEncounterRegistry.get(level);
        Iterator<LichEncounterRegistry.PendingEncounter> iterator = registry.getPending().iterator();
        while (iterator.hasNext()) {
            LichEncounterRegistry.PendingEncounter encounter = iterator.next();

            // Критично: если чанк башни не загружен — лучники сериализованы на
            // диск и level.getEntity(uuid) даёт null для ВСЕХ живых. Без этой
            // проверки Лич спавнился бы сразу после появления башни (она
            // возникает далеко от игрока, чанки тут же выгружаются). Считать
            // лучников "проверяемыми" можно только пока чанк реально загружен —
            // то есть когда игрок уже пришёл к башне.
            if (!level.isLoaded(encounter.lichSpawnPos)) continue;

            boolean anyAlive = encounter.archers.stream().anyMatch(id -> level.getEntity(id) != null);
            if (anyAlive) continue;

            spawnLich(level, encounter.lichSpawnPos);
            iterator.remove();
            registry.setDirty();
        }
    }

    private static void spawnLich(ServerLevel level, BlockPos pos) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(LICH_ID);
        if (type == null) return; // мод босса не установлен — тихо пропускаем

        Entity entity = type.create(level);
        if (entity == null) return;
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
    }
}
