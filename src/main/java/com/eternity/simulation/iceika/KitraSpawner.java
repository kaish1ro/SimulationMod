package com.eternity.simulation.iceika;

import com.eternity.simulation.SimulationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * divinerpg:kitra в самом DivineRPG нигде не спавнится в мире (нет ни в
 * списках спавна биомов, ни встроенной в NBT структуры — только спавн-яйцо).
 * Структура {@code divinerpg:iceika/whale_skull} генерируется штатно (обычный
 * worldgen) и служит нам просто "маркером" биома boneyard, где Китру уместно
 * заспавнить — сама структура пустая, просто декоративная свалка костей.
 *
 * <p>В биоме boneyard таких структур очень много (это его обычная застройка),
 * поэтому спавним не на каждый экземпляр, а один-единственный раз на всё
 * измерение — см. {@link KitraSpawnRegistry}. Достаточно "какой-то" структуры
 * как места старта: {@code start.getChunkPos()} всё равно даёт валидную точку
 * внутри самого биома boneyard.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class KitraSpawner {

    private static final ResourceLocation WHALE_SKULL_STRUCTURE_ID =
            new ResourceLocation("divinerpg", "iceika/whale_skull");
    private static final ResourceLocation KITRA_ID =
            new ResourceLocation("divinerpg", "kitra");

    private static final double MAX_HEALTH = 300.0;
    private static final double ATTACK_DAMAGE = 10.0;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkAccess chunk = event.getChunk();

        Structure whaleSkull = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(WHALE_SKULL_STRUCTURE_ID);
        if (whaleSkull == null) return;

        StructureStart start = chunk.getStartForStructure(whaleSkull);
        if (start == null || !start.isValid()) return;
        // Стартовый чанк структуры прогружается один раз на экземпляр — если
        // проверять по любому чанку из bounding box, сюда прилетало бы много раз.
        if (!start.getChunkPos().equals(chunk.getPos())) return;

        BoundingBox box = start.getBoundingBox();

        // ChunkEvent.Load может прилететь до того, как чанк реально промотан
        // в FULL — трогать уровень (спавнить сущность) здесь напрямую нельзя,
        // это вызывает дедлоки загрузки чанков. Откладываем на следующий тик.
        level.getServer().execute(() -> {
            KitraSpawnRegistry registry = KitraSpawnRegistry.get(level);
            if (registry.isSpawned()) return; // одна Китра на всё измерение
            registry.markSpawned();
            spawnKitra(level, box);
        });
    }

    private static void spawnKitra(ServerLevel level, BoundingBox box) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(KITRA_ID);
        if (type == null) return; // DivineRPG не установлен — тихо пропускаем

        Entity entity = type.create(level);
        if (entity == null) return;

        int x = (box.minX() + box.maxX()) / 2;
        int z = (box.minZ() + box.maxZ()) / 2;
        int y = box.maxY() + 2; // чуть выше груды костей, в толще воды
        entity.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);

        if (entity instanceof LivingEntity living) {
            AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(MAX_HEALTH);
            living.setHealth((float) MAX_HEALTH);

            AttributeInstance attackDamage = living.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage != null) attackDamage.setBaseValue(ATTACK_DAMAGE);
        }

        level.addFreshEntity(entity);
    }
}
