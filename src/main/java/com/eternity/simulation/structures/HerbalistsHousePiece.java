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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.registries.ForgeRegistries;

public class HerbalistsHousePiece extends TemplateStructurePiece {

    // package-private: используется и в HerbalistsHouseStructure для проверки футпринта
    static final ResourceLocation TEMPLATE =
            new ResourceLocation(SimulationMod.MODID, "herbalists_house");
    private static final ResourceLocation SUPPLIES_LOOT =
            new ResourceLocation(SimulationMod.MODID, "chests/herbalists_house_supplies");

    // Блок подсыпки фундамента. Depthrock — родной камень Undergarden, сливается с
    // рельефом; minecraft:stone как фолбэк, если мода Undergarden нет (урезанный пак).
    private static final ResourceLocation DEPTHROCK = new ResourceLocation("undergarden", "depthrock");
    // На сколько блоков максимум подсыпаем вниз, чтобы под обрывом не вырастала
    // абсурдно длинная колонна (выборка площадки и так отсекает большие перепады).
    private static final int MAX_FOUNDATION_DEPTH = 12;

    private static BlockState foundationState; // лениво, реестр блоков готов на этапе генерации

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

    // Сначала ставим сам шаблон (super), затем подсыпаем фундамент. Дом стоит по
    // самой высокой точке пола (см. HerbalistsHouseStructure), поэтому под более
    // низкими краями остаётся пустота — раньше это и был «ров»/висящий над обрывом
    // дом. Заполняем её depthrock'ом вниз до грунта по всему футпринту 15×15.
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);

        BoundingBox bb = this.getBoundingBox();
        int baseY = bb.minY(); // нижний слой фундамента шаблона (отн. Y=0)
        BlockState foundation = foundationState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int wx = bb.minX(); wx <= bb.maxX(); wx++) {
            for (int wz = bb.minZ(); wz <= bb.maxZ(); wz++) {
                // box ограничен текущим чанком — столбцы вне его обработаются,
                // когда сгенерируется их чанк (postProcess зовётся для каждого).
                for (int wy = baseY - 1; wy >= baseY - MAX_FOUNDATION_DEPTH; wy--) {
                    m.set(wx, wy, wz);
                    if (!box.isInside(m)) continue;
                    BlockState cur = level.getBlockState(m);
                    // Дошли до настоящего грунта — ниже подсыпать не нужно.
                    if (!cur.isAir() && cur.getFluidState().isEmpty() && !cur.canBeReplaced()) break;
                    level.setBlock(m, foundation, 2);
                }
            }
        }
    }

    private static BlockState foundationState() {
        if (foundationState == null) {
            Block b = ForgeRegistries.BLOCKS.containsKey(DEPTHROCK)
                    ? ForgeRegistries.BLOCKS.getValue(DEPTHROCK)
                    : Blocks.STONE;
            foundationState = (b == null ? Blocks.STONE : b).defaultBlockState();
        }
        return foundationState;
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
