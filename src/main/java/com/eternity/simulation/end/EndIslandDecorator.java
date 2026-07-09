package com.eternity.simulation.end;

import com.eternity.simulation.SimulationSavedData;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class EndIslandDecorator {
    private static final int DEFAULT_RADIUS = 100;
    private static final int MIN_RADIUS = 48;
    private static final int MAX_RADIUS = 160;
    private static final int INNER_SAFE_RADIUS = 24;
    private static final int PILLAR_SAFE_RADIUS = 9;
    private static final int COLUMNS_PER_TICK = 900;

    private static final int[][] VANILLA_PILLARS = {
        {42, 0}, {34, 25}, {13, 40}, {-13, 40}, {-34, 25},
        {-42, 0}, {-34, -25}, {-13, -40}, {13, -40}, {34, -25}
    };

    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final Set<Long> queuedAutoChunks = new HashSet<>();
    private static ServerLevel activeLevel;
    private static CommandSourceStack feedbackSource;
    private static int totalColumns;
    private static int scannedColumns;
    private static int changedBlocks;

    private EndIslandDecorator() {}

    public static int defaultRadius() {
        return DEFAULT_RADIUS;
    }

    public static int minRadius() {
        return MIN_RADIUS;
    }

    public static int maxRadius() {
        return MAX_RADIUS;
    }

    public static int start(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel end = source.getServer().getLevel(Level.END);
        if (end == null) {
            source.sendFailure(Component.literal("[simend] The End level is not loaded."));
            return 0;
        }

        if (!queue.isEmpty()) {
            source.sendFailure(Component.literal("[simend] Decoration is already running."));
            return 0;
        }

        int clampedRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        queue.clear();
        for (int x = -clampedRadius; x <= clampedRadius; x++) {
            for (int z = -clampedRadius; z <= clampedRadius; z++) {
                if (x * x + z * z <= clampedRadius * clampedRadius) {
                    queue.add(pack(x, z));
                }
            }
        }

        activeLevel = end;
        feedbackSource = source;
        totalColumns = queue.size();
        scannedColumns = 0;
        changedBlocks = 0;
        markIntersectingChunksDecorated(end, clampedRadius);

        source.sendSuccess(() -> Component.literal(
            "[simend] Queued central End island decoration: radius=" + clampedRadius
                + ", columns=" + totalColumns + "."), true);
        return totalColumns;
    }

    public static void enqueueGeneratedChunk(ServerLevel level, ChunkPos chunkPos) {
        if (level.dimension() != Level.END) {
            return;
        }

        if (!intersectsDecorationRadius(chunkPos, DEFAULT_RADIUS)) {
            return;
        }

        long chunkKey = chunkPos.toLong();
        if (!queuedAutoChunks.add(chunkKey)) {
            return;
        }

        SimulationSavedData data = SimulationSavedData.get(level);
        if (data.isEndIslandChunkDecorated(chunkKey)) {
            return;
        }
        data.markEndIslandChunkDecorated(chunkKey);

        if (activeLevel == null) {
            activeLevel = level;
        }

        int before = queue.size();
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        int radiusSq = DEFAULT_RADIUS * DEFAULT_RADIUS;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x * x + z * z <= radiusSq) {
                    queue.add(pack(x, z));
                }
            }
        }

        totalColumns += queue.size() - before;
    }

    public static void tick() {
        if (activeLevel == null || queue.isEmpty()) {
            return;
        }

        int budget = COLUMNS_PER_TICK;
        while (budget-- > 0 && !queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = unpackX(packed);
            int z = unpackZ(packed);
            activeLevel.getChunk(x >> 4, z >> 4);
            changedBlocks += decorateColumn(activeLevel, x, z);
            scannedColumns++;
        }

        if (queue.isEmpty()) {
            CommandSourceStack source = feedbackSource;
            if (source != null) {
                source.sendSuccess(() -> Component.literal(
                    "[simend] End island decoration complete: columns=" + scannedColumns
                        + ", changedBlocks=" + changedBlocks + "."), true);
            }
            activeLevel = null;
            feedbackSource = null;
            queuedAutoChunks.clear();
            totalColumns = 0;
            scannedColumns = 0;
            changedBlocks = 0;
        }
    }

    private static boolean intersectsDecorationRadius(ChunkPos chunkPos, int radius) {
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        int closestX = clamp(0, minX, maxX);
        int closestZ = clamp(0, minZ, maxZ);
        return closestX * closestX + closestZ * closestZ <= radius * radius;
    }

    private static void markIntersectingChunksDecorated(ServerLevel level, int radius) {
        SimulationSavedData data = SimulationSavedData.get(level);
        int minChunk = Math.floorDiv(-radius, 16);
        int maxChunk = Math.floorDiv(radius, 16);
        for (int chunkX = minChunk; chunkX <= maxChunk; chunkX++) {
            for (int chunkZ = minChunk; chunkZ <= maxChunk; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                if (intersectsDecorationRadius(chunkPos, radius)) {
                    data.markEndIslandChunkDecorated(chunkPos.toLong());
                }
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int decorateColumn(ServerLevel level, int x, int z) {
        if (isProtectedXZ(x, z)) {
            return 0;
        }

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (y < 45 || y > 95) {
            return 0;
        }

        BlockPos topPos = new BlockPos(x, y, z);
        BlockState top = level.getBlockState(topPos);
        if (!isEndSurface(top)) {
            return 0;
        }

        int changed = 0;
        double stonePatch = blobValue(level.getSeed(), x, z, 17L, 17, 5, 12);
        double obsidianPatch = blobValue(level.getSeed(), x, z, 41L, 27, 3, 7);
        double crystalPatch = blobValue(level.getSeed(), x, z, 67L, 31, 4, 9);
        double mound = blobValue(level.getSeed(), x, z, 93L, 23, 4, 11);

        BlockState newTop = pickSurfaceState(level.getSeed(), x, z, stonePatch, obsidianPatch, crystalPatch);
        if (newTop != null && !top.is(newTop.getBlock()) && canReplaceSurface(top)) {
            level.setBlock(topPos, newTop, Block.UPDATE_CLIENTS);
            changed++;
        }

        if (mound > 0.52D && stonePatch > 0.15D) {
            int layers = mound > 0.82D ? 3 : mound > 0.68D ? 2 : 1;
            changed += placeMound(level, topPos, layers, x, z);
        }

        long h = hash(level.getSeed(), x, z, 151L);
        if ((h & 1023L) < 5L && stonePatch > 0.25D && mound < 0.65D) {
            changed += placeChorus(level, topPos.above(), h);
        }

        if (crystalPatch > 0.48D && (hash(level.getSeed(), x, z, 509L) & 255L) < 18L) {
            changed += placeCrystalCluster(level, topPos.above(), x, z);
        }

        return changed;
    }

    private static BlockState pickSurfaceState(long seed, int x, int z, double stonePatch, double obsidianPatch, double crystalPatch) {
        long h = hash(seed, x, z, 211L);
        int roll = (int) Math.floorMod(h, 100);

        if (crystalPatch > 0.62D) {
            BlockState allurite = optionalBlockState("galosphere:allurite_block");
            BlockState smoothAllurite = optionalBlockState("galosphere:smooth_allurite");
            BlockState smoothAmethyst = optionalBlockState("galosphere:smooth_amethyst");
            if (roll < 22) return Blocks.AMETHYST_BLOCK.defaultBlockState();
            if (roll < 42 && allurite != null) return allurite;
            if (roll < 58) return Blocks.CALCITE.defaultBlockState();
            if (roll < 72 && smoothAllurite != null) return smoothAllurite;
            if (roll < 82 && smoothAmethyst != null) return smoothAmethyst;
            return Blocks.END_STONE.defaultBlockState();
        }

        if (obsidianPatch > 0.70D) {
            if (roll < 8) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
            if (roll < 34) return Blocks.OBSIDIAN.defaultBlockState();
            if (roll < 62) return Blocks.SMOOTH_BASALT.defaultBlockState();
            return Blocks.END_STONE.defaultBlockState();
        }

        if (stonePatch > 0.35D) {
            if (roll < 12) return Blocks.TUFF.defaultBlockState();
            if (roll < 25) return Blocks.ANDESITE.defaultBlockState();
            if (roll < 36) return Blocks.COBBLESTONE.defaultBlockState();
            if (roll < 46) return Blocks.SMOOTH_BASALT.defaultBlockState();
            if (roll < 55) return Blocks.END_STONE_BRICKS.defaultBlockState();
        }

        return null;
    }

    private static int placeMound(ServerLevel level, BlockPos topPos, int layers, int x, int z) {
        int changed = 0;
        for (int i = 1; i <= layers; i++) {
            BlockPos pos = topPos.above(i);
            if (!level.getBlockState(pos).isAir()) {
                break;
            }

            BlockState state = moundState(level.getSeed(), x, z, i);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            changed++;
        }
        return changed;
    }

    private static BlockState moundState(long seed, int x, int z, int layer) {
        int roll = (int) Math.floorMod(hash(seed, x, z, 307L + layer), 100);
        if (roll < 12) return Blocks.TUFF.defaultBlockState();
        if (roll < 24) return Blocks.SMOOTH_BASALT.defaultBlockState();
        if (roll < 34) return Blocks.ANDESITE.defaultBlockState();
        return Blocks.END_STONE.defaultBlockState();
    }

    private static int placeChorus(ServerLevel level, BlockPos basePos, long h) {
        BlockPos support = basePos.below();
        if (!isEndSurface(level.getBlockState(support)) || !level.getBlockState(basePos).isAir()) {
            return 0;
        }

        int height = 3 + (int) Math.floorMod(h >> 12, 4);
        for (int i = 0; i < height; i++) {
            if (!level.getBlockState(basePos.above(i)).isAir()) {
                return 0;
            }
        }

        int changed = 0;
        for (int i = 0; i < height; i++) {
            BlockPos pos = basePos.above(i);
            BlockState state = i == height - 1
                ? Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5)
                : Blocks.CHORUS_PLANT.defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            changed++;
        }
        return changed;
    }

    private static int placeCrystalCluster(ServerLevel level, BlockPos pos, int x, int z) {
        if (!level.getBlockState(pos).isAir()) {
            return 0;
        }

        BlockState support = level.getBlockState(pos.below());
        if (!isCrystalSupport(support)) {
            return 0;
        }

        long h = hash(level.getSeed(), x, z, 613L);
        BlockState cluster;
        if ((h & 3L) == 0L) {
            cluster = optionalBlockState("galosphere:allurite_cluster");
        } else if ((h & 7L) == 1L) {
            cluster = optionalBlockState("galosphere:glinted_allurite_cluster");
        } else {
            cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState();
        }

        if (cluster == null) {
            cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState();
        }
        if (cluster.hasProperty(BlockStateProperties.FACING)) {
            cluster = cluster.setValue(BlockStateProperties.FACING, Direction.UP);
        }
        if (cluster.hasProperty(BlockStateProperties.WATERLOGGED)) {
            cluster = cluster.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        level.setBlock(pos, cluster, Block.UPDATE_CLIENTS);
        return 1;
    }

    private static boolean isProtectedXZ(int x, int z) {
        if (x * x + z * z < INNER_SAFE_RADIUS * INNER_SAFE_RADIUS) {
            return true;
        }
        for (int[] pillar : VANILLA_PILLARS) {
            int dx = x - pillar[0];
            int dz = z - pillar[1];
            if (dx * dx + dz * dz < PILLAR_SAFE_RADIUS * PILLAR_SAFE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEndSurface(BlockState state) {
        return state.is(Blocks.END_STONE)
            || state.is(Blocks.END_STONE_BRICKS)
            || state.is(Blocks.TUFF)
            || state.is(Blocks.ANDESITE)
            || state.is(Blocks.COBBLESTONE)
            || state.is(Blocks.SMOOTH_BASALT)
            || state.is(Blocks.CALCITE)
            || state.is(Blocks.AMETHYST_BLOCK)
            || isOptionalBlock(state, "galosphere:allurite_block")
            || isOptionalBlock(state, "galosphere:smooth_allurite")
            || isOptionalBlock(state, "galosphere:smooth_amethyst");
    }

    private static boolean isCrystalSupport(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK)
            || state.is(Blocks.CALCITE)
            || isOptionalBlock(state, "galosphere:allurite_block")
            || isOptionalBlock(state, "galosphere:smooth_allurite")
            || isOptionalBlock(state, "galosphere:smooth_amethyst");
    }

    private static boolean canReplaceSurface(BlockState state) {
        return !state.is(Blocks.BEDROCK)
            && !state.is(Blocks.OBSIDIAN)
            && !state.is(Blocks.CRYING_OBSIDIAN)
            && !state.is(Blocks.END_PORTAL)
            && !state.is(Blocks.END_PORTAL_FRAME)
            && !state.is(Blocks.IRON_BARS);
    }

    private static BlockState optionalBlockState(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        return block.defaultBlockState();
    }

    private static boolean isOptionalBlock(BlockState state, String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return block != null && block != Blocks.AIR && state.is(block);
    }

    private static double blobValue(long seed, int x, int z, long salt, int grid, int minRadius, int maxRadius) {
        int cellX = Math.floorDiv(x, grid);
        int cellZ = Math.floorDiv(z, grid);
        double best = 0.0D;

        for (int gx = cellX - 1; gx <= cellX + 1; gx++) {
            for (int gz = cellZ - 1; gz <= cellZ + 1; gz++) {
                long h = hash(seed, gx, gz, salt);
                int centerX = gx * grid + (int) Math.floorMod(h, grid);
                int centerZ = gz * grid + (int) Math.floorMod(h >> 8, grid);
                int radius = minRadius + (int) Math.floorMod(h >> 16, maxRadius - minRadius + 1);
                double dx = x - centerX;
                double dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz) / radius;
                if (distance < 1.0D) {
                    best = Math.max(best, 1.0D - distance);
                }
            }
        }

        return best;
    }

    private static long hash(long seed, int x, int z, long salt) {
        long value = seed ^ salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
