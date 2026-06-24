package com.eternity.simulation.blocks;

import com.eternity.simulation.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Блок двери замка с тремя состояниями:
 *
 * <ol>
 *   <li><b>Заперта</b> ({@code open=false}, BE.locked=true) — ПКМ с правильным ключом
 *       разблокирует всю связную группу (BFS по door_id). Ключ потребляется.</li>
 *   <li><b>Разблокирована, закрыта</b> ({@code open=false}, BE.locked=false) — ПКМ без ключа
 *       открывает всю группу. Дверь остаётся видимой, но без коллизии.</li>
 *   <li><b>Открыта</b> ({@code open=true}) — через 5 секунд автоматически закрывается
 *       (scheduled tick). Коллизии нет, базовая текстура скрыта, видна только рамка.</li>
 * </ol>
 *
 * <p>CTM-соединения рассчитываются динамически через
 * {@link com.eternity.simulation.client.model.CastleKeyDoorBakedModel}.
 */
public class CastleKeyDoorBlock extends Block implements EntityBlock {

    public static final BooleanProperty OPEN     = BooleanProperty.create("open");
    public static final BooleanProperty VANISHING = BooleanProperty.create("vanishing");

    /** Маленький куб в центре блока — визуальный «хитбокс» в момент анимации исчезновения (как в TF). */
    private static final VoxelShape VANISHING_BB = Block.box(6, 6, 6, 10, 10, 10);

    /** 5 секунд = 100 тиков при 20 TPS. */
    private static final int AUTO_CLOSE_TICKS  = 100;
    /** Длительность анимации исчезновения/появления (тиков). */
    private static final int VANISH_ANIM_TICKS = 5;
    private static final int MAX_FLOOD         = 128;

    public CastleKeyDoorBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(VANISHING, false));
    }

    // ── EntityBlock ───────────────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastleKeyDoorBlockEntity(pos, state);
    }

    // ── Blockstate ────────────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, VANISHING);
    }

    // ── Коллизия и форма ─────────────────────────────────────────────────────

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    /** В момент анимации — маленький куб (имитация TF-эффекта «схлопывания»), иначе — полный. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext ctx) {
        return state.getValue(VANISHING) ? VANISHING_BB : Shapes.block();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level,
                                     BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    // ── Рендер ───────────────────────────────────────────────────────────────

    /** Дверь всегда рендерится (базовая текстура скрывается через BakedModel по OPEN). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return state.getValue(OPEN);
    }

    /** Смежные закрытые двери скрывают общую грань. Открытые рендерят свои (видна рамка). */
    @Override
    public boolean skipRendering(BlockState state, BlockState adj, Direction dir) {
        if (!state.getValue(OPEN)
                && adj.getBlock() instanceof CastleKeyDoorBlock
                && !adj.getValue(OPEN)) {
            return true;
        }
        return super.skipRendering(state, adj, dir);
    }

    // ── Взаимодействие ────────────────────────────────────────────────────────

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        boolean open = state.getValue(OPEN);

        // Открытая дверь: ПКМ не нужен — закроется сама по таймеру
        if (open) return InteractionResult.PASS;

        boolean locked = isLocked(level, pos);

        if (locked) {
            // ── Попытка разблокировать ключом ────────────────────────────────
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() != ModItems.CASTLE_KEY.get()) {
                if (level.isClientSide()) {
                    player.sendSystemMessage(Component.literal("§8Заперто. Нужен ключ от замка."));
                }
                return InteractionResult.SUCCESS;
            }

            String keyId   = getKeyDoorId(held);
            String blockId = getDoorId(level, pos);
            if (!keyId.equals(blockId)) {
                if (level.isClientSide()) {
                    player.sendSystemMessage(Component.literal("§8Этот ключ не подходит."));
                }
                return InteractionResult.SUCCESS;
            }

            if (!level.isClientSide()) {
                bfsUnlock(level, pos, blockId);
                if (!player.isCreative()) held.shrink(1);
                // Магический звук разблокировки
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS, 1.0f, 0.5f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // ── Разблокирована — открываем ────────────────────────────────────────
        if (!level.isClientSide()) {
            String doorId = getDoorId(level, pos);
            bfsOpen(level, pos, doorId);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS, 1.0f, 0.75f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ── Scheduled tick — автозакрытие ─────────────────────────────────────────

    /**
     * Машина состояний анимации двери (аналог TF CastleDoorBlock):
     * <pre>
     *  OPEN=false, VANISHING=true  → открытие: ставим OPEN=true, VANISHING=false, планируем AUTO_CLOSE
     *  OPEN=true,  VANISHING=false → закрытие: ставим VANISHING=true, планируем VANISH_ANIM_TICKS
     *  OPEN=true,  VANISHING=true  → анимация завершена: ставим OPEN=false, VANISHING=false
     * </pre>
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean open     = state.getValue(OPEN);
        boolean vanishing = state.getValue(VANISHING);

        if (!open && vanishing) {
            // Анимация открытия завершена → дверь полностью открыта
            level.setBlock(pos, state.setValue(OPEN, true).setValue(VANISHING, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.BLOCKS, 0.6f, 1.3f);
            level.scheduleTick(pos, this, AUTO_CLOSE_TICKS);
        } else if (open && !vanishing) {
            // Автозакрытие: запускаем анимацию исчезновения
            level.setBlock(pos, state.setValue(VANISHING, true), Block.UPDATE_ALL);
            spawnVanishParticles(level, pos);
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.BLOCKS, 0.5f, 1.5f);
            level.scheduleTick(pos, this, VANISH_ANIM_TICKS);
        } else if (open && vanishing) {
            // Анимация закрытия завершена → полностью закрыта
            level.setBlock(pos, state.setValue(OPEN, false).setValue(VANISHING, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, 0.6f, 0.9f);
        }
    }

    // ── BFS вспомогательные ───────────────────────────────────────────────────

    /**
     * Разблокировать (locked=false) все связные двери с тем же door_id.
     * Дверь остаётся закрытой.
     */
    private static void bfsUnlock(Level level, BlockPos start, String doorId) {
        Queue<BlockPos> queue   = new ArrayDeque<>();
        Set<BlockPos>   visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() <= MAX_FLOOD) {
            BlockPos cur = queue.poll();

            if (level.getBlockEntity(cur) instanceof CastleKeyDoorBlockEntity be) {
                be.setLocked(false);
            }

            enqueueNeighbors(level, cur, doorId, visited, queue);
        }
    }

    /**
     * Открыть все разблокированные связные двери (BFS).
     * Каждый открытый блок получает scheduled tick для автозакрытия.
     */
    private static void bfsOpen(Level level, BlockPos start, String doorId) {
        Queue<BlockPos> queue   = new ArrayDeque<>();
        Set<BlockPos>   visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() <= MAX_FLOOD) {
            BlockPos cur = queue.poll();

            BlockState cs = level.getBlockState(cur);
            if (cs.getBlock() instanceof CastleKeyDoorBlock && !cs.getValue(OPEN) && !cs.getValue(VANISHING)) {
                // Запускаем анимацию исчезновения: VANISHING=true → через 5 тиков → OPEN=true
                level.setBlock(cur, cs.setValue(VANISHING, true), Block.UPDATE_ALL);
                if (level instanceof ServerLevel sl) {
                    spawnVanishParticles(sl, cur);
                    sl.playSound(null, cur, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.BLOCKS, 0.5f, 1.5f);
                    sl.scheduleTick(cur, cs.getBlock(), VANISH_ANIM_TICKS);
                }
            }

            // Распространяем только по разблокированным дверям
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (visited.contains(nb)) continue;
                if (isConnectableUnlocked(level, nb, doorId)) {
                    visited.add(nb);
                    queue.add(nb);
                }
            }
        }
    }

    /** Добавляет всех соседей с тем же door_id в очередь BFS. */
    private static void enqueueNeighbors(Level level, BlockPos pos, String doorId,
                                         Set<BlockPos> visited, Queue<BlockPos> queue) {
        for (Direction dir : Direction.values()) {
            BlockPos nb = pos.relative(dir);
            if (visited.contains(nb)) continue;
            if (isConnectableDoor(level, nb, doorId)) {
                visited.add(nb);
                queue.add(nb);
            }
        }
    }

    /** Любая дверь с тем же door_id (для unlock BFS). */
    private static boolean isConnectableDoor(LevelAccessor level, BlockPos pos, String doorId) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CastleKeyDoorBlock)) return false;
        return doorId.equals(getDoorId(level, pos));
    }

    /** Разблокированная закрытая (и не анимирующаяся) дверь с тем же door_id (для open BFS). */
    private static boolean isConnectableUnlocked(Level level, BlockPos pos, String doorId) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CastleKeyDoorBlock)) return false;
        if (s.getValue(OPEN) || s.getValue(VANISHING)) return false; // уже в процессе
        if (!doorId.equals(getDoorId(level, pos))) return false;
        return !isLocked(level, pos);
    }

    // ── Частицы ──────────────────────────────────────────────────────────────

    /**
     * Сетка частиц 4×4×4 по всему объёму блока — точная копия алгоритма TF CastleDoorBlock.
     * Использует PORTAL вместо TFParticleType.ANNIHILATE (TF не является compile-зависимостью).
     */
    private static void spawnVanishParticles(ServerLevel level, BlockPos pos) {
        RandomSource rand = level.getRandom();
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                for (int dz = 0; dz < 4; dz++) {
                    double x = pos.getX() + (dx + 0.5D) / 4;
                    double y = pos.getY() + (dy + 0.5D) / 4;
                    double z = pos.getZ() + (dz + 0.5D) / 4;
                    double speed = rand.nextGaussian() * 0.2D;
                    level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, speed);
                }
            }
        }
    }

    // ── Вспомогательные утилиты ───────────────────────────────────────────────

    /** Проверяет, заперта ли дверь (по BlockEntity). Дефолт — заперта. */
    private static boolean isLocked(LevelAccessor level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CastleKeyDoorBlockEntity be) {
            return be.isLocked();
        }
        return true;
    }

    /** door_id из BlockEntity; если нет — DEFAULT_ID. */
    static String getDoorId(LevelAccessor level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CastleKeyDoorBlockEntity be) {
            return be.getDoorId();
        }
        return CastleKeyDoorBlockEntity.DEFAULT_ID;
    }

    /** door_id из NBT ключа. */
    private static String getKeyDoorId(ItemStack key) {
        if (key.hasTag() && key.getTag().contains("door_id")) {
            String id = key.getTag().getString("door_id");
            if (!id.isEmpty()) return id;
        }
        return CastleKeyDoorBlockEntity.DEFAULT_ID;
    }
}
