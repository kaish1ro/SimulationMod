package com.eternity.simulation.structures;

import com.eternity.simulation.ModVillagers;
import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class HerbalistsHousePiece extends TemplateStructurePiece {

    private static final ResourceLocation TEMPLATE =
            new ResourceLocation(SimulationMod.MODID, "herbalists_house");
    private static final ResourceLocation SUPPLIES_LOOT =
            new ResourceLocation(SimulationMod.MODID, "chests/herbalists_house_supplies");

    public HerbalistsHousePiece(StructureTemplateManager manager, BlockPos origin) {
        super(ModStructures.HERBALISTS_HOUSE_PIECE.get(), 0, manager,
                TEMPLATE, TEMPLATE.toString(),
                new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE),
                origin);
    }

    public HerbalistsHousePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.HERBALISTS_HOUSE_PIECE.get(), tag, context.structureTemplateManager(),
                rl -> new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(Rotation.NONE));
    }

    // Вызывается базовым postProcess для каждого структурного блока в режиме DATA.
    // Структурный блок НЕ удаляется автоматически — убираем его в AIR вручную.
    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        switch (marker) {
            case "villager_spawn" -> {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                spawnHerbalist(level, pos, random);
            }
            case "loot_table" -> {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                fillChests(level, pos, random);
            }
        }
    }

    // Двойной сундук уже размещён шаблоном на блок ниже маркера ([x,2,8] и [x,2,7]).
    // Просто назначаем им лут-таблицу.
    private void fillChests(ServerLevelAccessor level, BlockPos markerPos, RandomSource random) {
        BlockPos chestA = markerPos.below();                       // под маркером
        BlockPos chestB = markerPos.below().relative(Direction.NORTH); // z-1

        for (BlockPos cp : new BlockPos[]{chestA, chestB}) {
            if (level.getBlockEntity(cp) instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(SUPPLIES_LOOT, random.nextLong());
            }
        }
    }

    private static void spawnHerbalist(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager == null) return;

        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        villager.setVillagerData(villager.getVillagerData()
                .setType(VillagerType.PLAINS)
                .setProfession(ModVillagers.STRANGE_HERBALIST.get())
                .setLevel(1));
        // xp > 0, чтобы ResetProfession (xp==0 && level<=1 && нет job site) никогда не разжаловал жителя
        villager.setVillagerXp(1);
        villager.setPersistenceRequired();
        villager.restrictTo(pos, 6);

        level.addFreshEntityWithPassengers(villager);
    }
}
