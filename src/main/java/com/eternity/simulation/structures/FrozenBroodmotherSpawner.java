package com.eternity.simulation.structures;

import com.eternity.simulation.ModBlocks;
import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Спавн вмёрзшей в лёд Ice Brood Mother + проверка «оттаяла ли» (нет больше
 * крепкого льда, перекрывающего её хитбокс) + обвал всей глыбы, если растаяла
 * треть льда — вызывается из {@code SolidIceBlock} при расплавлении льда
 * сверхгорячей лавой.
 */
public final class FrozenBroodmotherSpawner {

    public static final String FROZEN_TAG = "simulation_frozen";

    private static final ResourceLocation ICE_BROOD_MOTHER_ID =
            new ResourceLocation("threateningly_mobs", "ice_brood_mother");

    private static final ResourceKey<Structure> FROZEN_BROODMOTHER_KEY =
            ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(SimulationMod.MODID, "frozen_broodmother"));

    // Точное число блоков simulation:solid_ice в frozen_broodmother.nbt (проверено
    // распаковкой файла) — порог обвала = треть от этого числа.
    private static final int TOTAL_ICE_BLOCKS = 5819;
    private static final int COLLAPSE_THRESHOLD = TOTAL_ICE_BLOCKS / 3;

    // Радиус поиска вмёрзших мобов вокруг растаявшего блока льда — модель
    // боссихи 17×13, берём с запасом.
    private static final double UNFREEZE_SEARCH_RADIUS = 20.0;

    private FrozenBroodmotherSpawner() {}

    public static void spawnFrozen(ServerLevelAccessor level, BlockPos pos) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ICE_BROOD_MOTHER_ID);
        if (type == null) return; // мода нет в сборке — тихо пропускаем

        var entity = type.create(level.getLevel());
        if (!(entity instanceof Mob mob)) return;

        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);

        var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(300.0D);
        mob.setHealth(300.0F);

        mob.setInvulnerable(true);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean(FROZEN_TAG, true);

        level.addFreshEntity(mob);
    }

    /**
     * Зовётся при расплавлении блока крепкого льда. Если блок относится к
     * глыбе frozen_broodmother — считает прогресс и при достижении трети
     * обваливает всю глыбу целиком. В любом случае проверяет, не освободилась
     * ли боссиха рядом (одного растаявшего блока тоже может хватить).
     */
    public static void onIceMelted(ServerLevel level, BlockPos meltedPos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(meltedPos, FROZEN_BROODMOTHER_KEY);
        if (start.isValid()) {
            BlockPos key = start.getBoundingBox().getCenter();
            FrozenBroodmotherProgressData progress = FrozenBroodmotherProgressData.get(level);
            if (!progress.isCollapsed(key)) {
                int destroyed = progress.incrementDestroyed(key);
                if (destroyed >= COLLAPSE_THRESHOLD) {
                    progress.markCollapsed(key);
                    collapseGlob(level, start.getBoundingBox());
                    return; // обвал уже разморозил всё, что было внутри
                }
            }
        }

        checkUnfreezeNear(level, meltedPos);
    }

    // Игрок топит лёд именно сверхгорячей лавой — после обвала вокруг обычно
    // остаются лужи/потёки этой жидкости. Чистим с запасом за пределами
    // bounding box самой глыбы, а не только внутри неё.
    private static final int LAVA_CLEANUP_MARGIN = 8;

    /** Треть льда растаяла — добиваем всю глыбу: каждый блок льда/снега разлетается со звуком. */
    private static void collapseGlob(ServerLevel level, BoundingBox bb) {
        level.playSound(null, bb.getCenter(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0F, 0.6F);
        level.playSound(null, bb.getCenter(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.8F);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int y = bb.minY(); y <= bb.maxY(); y++) {
                for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.SOLID_ICE.get()) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                        level.levelEvent(2001, cursor, Block.getId(state)); // частицы+звук разрушения блока
                        level.removeBlock(cursor, false);
                    }
                }
            }
        }

        clearSuperhotLava(level, bb);
        forceUnfreezeInBox(level, bb);
    }

    /** Убирает всю сверхгорячую лаву (источник и потоки) вокруг обвалившейся глыбы. */
    private static void clearSuperhotLava(ServerLevel level, BoundingBox bb) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bb.minX() - LAVA_CLEANUP_MARGIN; x <= bb.maxX() + LAVA_CLEANUP_MARGIN; x++) {
            for (int y = bb.minY() - LAVA_CLEANUP_MARGIN; y <= bb.maxY() + LAVA_CLEANUP_MARGIN; y++) {
                for (int z = bb.minZ() - LAVA_CLEANUP_MARGIN; z <= bb.maxZ() + LAVA_CLEANUP_MARGIN; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(ModBlocks.SUPERHOT_LAVA_BLOCK.get())) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** После полного обвала лёд гарантированно нигде не остался — освобождаем без проверки энкейсмента. */
    private static void forceUnfreezeInBox(ServerLevel level, BoundingBox bb) {
        AABB box = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
        List<Mob> frozen = level.getEntitiesOfClass(Mob.class, box,
                m -> m.getPersistentData().getBoolean(FROZEN_TAG));
        for (Mob mob : frozen) {
            unfreeze(level, mob);
        }
    }

    private static void checkUnfreezeNear(ServerLevel level, BlockPos meltedPos) {
        AABB searchBox = new AABB(meltedPos).inflate(UNFREEZE_SEARCH_RADIUS);
        List<Mob> frozen = level.getEntitiesOfClass(Mob.class, searchBox,
                m -> m.getPersistentData().getBoolean(FROZEN_TAG));

        for (Mob mob : frozen) {
            if (!isEncasedInIce(level, mob)) {
                unfreeze(level, mob);
            }
        }
    }

    private static boolean isEncasedInIce(ServerLevel level, Mob mob) {
        AABB box = mob.getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.ceil(box.maxX);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.ceil(box.maxY);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.ceil(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(ModBlocks.SOLID_ICE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void unfreeze(ServerLevel level, Mob mob) {
        mob.setInvulnerable(false);
        mob.setNoAi(false);
        mob.setSilent(false);
        mob.getPersistentData().remove(FROZEN_TAG);

        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 80, 2.0, 2.0, 2.0, 0.05);
    }
}
