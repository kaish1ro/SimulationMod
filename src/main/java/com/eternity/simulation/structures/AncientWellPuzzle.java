package com.eternity.simulation.structures;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Загадка древнего колодца: бросить звезду Незера в воду колодца → звезда
 * пропадает, звук повышения уровня, фрагмент карты Ферокса №3 падает под ноги
 * игрока, который её бросил. Один колодец разгадывается только один раз
 * (см. {@link AncientWellSolvedData}), иначе фрагмент можно было бы фармить.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class AncientWellPuzzle {

    // Раз в 10 тиков достаточно — звёзды Незера крайне редки, лишний запас не нужен.
    private static final int CHECK_INTERVAL = 10;

    // Игрок кидает звезду прямо себе под ноги в колодец — небольшой радиус
    // вокруг игрока с запасом. Раньше здесь был AABB на весь мир (±30 млн
    // блоков) — level.getEntities() с ним сканировал ВСЕ загруженные сущности
    // во всём уровне каждые 10 тиков; на сильно исследованном мире это давало
    // фризы (до 91% времени тика на один этот вызов, см. spark-профиль).
    private static final double SEARCH_RADIUS = 16.0;

    private static final ResourceKey<Structure> ANCIENT_WELL_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(SimulationMod.MODID, "ancient_well"));

    // Колодцы есть только в Undergarden — нет смысла тикать эту проверку
    // во всех остальных ~25 измерениях сборки.
    private static final ResourceKey<Level> UNDERGARDEN_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("undergarden", "undergarden"));

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (!level.dimension().equals(UNDERGARDEN_KEY)) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;

        for (Player player : level.players()) {
            AABB nearby = player.getBoundingBox().inflate(SEARCH_RADIUS);
            for (ItemEntity item : level.getEntities(EntityType.ITEM, nearby, AncientWellPuzzle::isCandidate)) {
                tryResolve(level, item);
            }
        }
    }

    private static boolean isCandidate(ItemEntity item) {
        return item.isAlive() && item.getItem().is(Items.NETHER_STAR) && item.isInWater();
    }

    private static void tryResolve(ServerLevel level, ItemEntity item) {
        BlockPos pos = item.blockPosition();
        StructureStart start = level.structureManager()
                .getStructureWithPieceAt(pos, ANCIENT_WELL_KEY);
        if (!start.isValid()) return;

        BlockPos wellKey = start.getBoundingBox().getCenter();
        AncientWellSolvedData data = AncientWellSolvedData.get(level);
        if (data.isSolved(wellKey)) return;

        data.markSolved(wellKey);

        // Звезда пропадает сразу
        ItemStack star = item.getItem().copy();
        star.shrink(1);
        if (star.isEmpty()) {
            item.discard();
        } else {
            item.setItem(star);
        }

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

        Entity thrower = item.getOwner();
        BlockPos dropPos = thrower instanceof Player player ? player.blockPosition() : pos;

        ItemEntity reward = new ItemEntity(level,
                dropPos.getX() + 0.5, dropPos.getY(), dropPos.getZ() + 0.5,
                new ItemStack(ModItems.VOID_BLOSSOM_MAP_FRAGMENT_3.get()));
        level.addFreshEntity(reward);
    }
}
