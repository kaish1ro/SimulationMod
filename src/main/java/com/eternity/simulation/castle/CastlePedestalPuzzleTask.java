package com.eternity.simulation.castle;

import com.eternity.simulation.SimulationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Головоломка с 4 пьедесталами (Supplementaries) синей башни: на каждый пьедестал
 * (на блок ниже соответствующего DATA-маркера с {@code item=<item_id>}) нужно положить
 * указанный предмет.
 *
 * <ul>
 *   <li>Если все 4 пьедестала заняты и предметы совпадают — головоломка решена один раз:
 *       выполняется {@link CastleRevealTask} (статуи/лестницы/завалы) и спавнится группа
 *       {@code blue_tower}.</li>
 *   <li>Если все 4 заняты, но хотя бы один предмет неверный — предметы сбрасываются на пол,
 *       а случайный онлайн-игрок поражается "божьей карой" (визуальная молния без огня +
 *       гарантированный урон с кастомным сообщением о смерти).</li>
 * </ul>
 */
public final class CastlePedestalPuzzleTask {

    private static final ResourceKey<DamageType> DIVINE_PUNISHMENT = ResourceKey.create(
        Registries.DAMAGE_TYPE, new ResourceLocation("simulation", "divine_punishment"));

    private static final int TICK_INTERVAL = 20;
    private static int tickCounter = 0;

    private CastlePedestalPuzzleTask() {}

    public static void tick() {
        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(CastleConstants.TWILIGHT_FOREST_DIM);
        if (level == null) return;

        SimulationSavedData data = SimulationSavedData.get(server.overworld());
        if (!data.isCastleSpawnSystemInit() || data.isBlueTowerPuzzleSolved()) return;

        List<CastleDataMarker> pedestals = new ArrayList<>();
        for (CastleDataMarker marker : data.getCastleMarkers()) {
            if (marker.has("item")) pedestals.add(marker);
        }
        if (pedestals.isEmpty()) return;

        List<ItemStack> placed = new ArrayList<>(pedestals.size());
        for (CastleDataMarker marker : pedestals) {
            ItemStack stack = getPedestalItem(level, marker.pos().below());
            if (stack.isEmpty()) return; // не все пьедесталы заполнены — ждём
            placed.add(stack);
        }

        boolean correct = true;
        for (int i = 0; i < pedestals.size(); i++) {
            String expected = pedestals.get(i).get("item");
            ResourceLocation actual = ForgeRegistries.ITEMS.getKey(placed.get(i).getItem());
            if (expected == null || actual == null || !actual.equals(new ResourceLocation(expected))) {
                correct = false;
                break;
            }
        }

        if (correct) {
            data.setBlueTowerPuzzleSolved(true);
            solveEffects(level, pedestals);
            CastleRevealTask.start(level, data.getCastleMarkers());
            CastleSpawnManager.triggerGroup(level, data, "blue_tower");
        } else {
            punish(level, server, pedestals);
        }
    }

    /** Звук разгадки + разрушение пьедесталов (блоки под маркерами с {@code item}). */
    private static void solveEffects(ServerLevel level, List<CastleDataMarker> pedestals) {
        for (CastleDataMarker marker : pedestals) {
            BlockPos pedestalPos = marker.pos().below();
            // Сначала убираем предмет, чтобы он не выпал при разрушении пьедестала.
            clearPedestalItem(level, pedestalPos);
            level.destroyBlock(pedestalPos, false);
        }

        // Мистический аккорд активации по центру головоломки.
        BlockPos center = pedestals.get(0).pos();
        level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.8f);

        // Оповещаем всех игроков о решении головоломки звуком, независимо от расстояния.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    private static ItemStack getPedestalItem(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return ItemStack.EMPTY;

        if (be instanceof Container container && container.getContainerSize() > 0) {
            return container.getItem(0);
        }

        return be.getCapability(ForgeCapabilities.ITEM_HANDLER)
            .map(h -> h.getSlots() > 0 ? h.getStackInSlot(0) : ItemStack.EMPTY)
            .orElse(ItemStack.EMPTY);
    }

    private static void clearPedestalItem(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        if (be instanceof Container container && container.getContainerSize() > 0) {
            container.setItem(0, ItemStack.EMPTY);
            container.setChanged();
            return;
        }

        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
            if (h.getSlots() > 0) h.extractItem(0, Integer.MAX_VALUE, false);
        });
    }

    /** Сбрасывает неверно расставленные предметы на пол и карает случайного игрока. */
    private static void punish(ServerLevel level, MinecraftServer server, List<CastleDataMarker> pedestals) {
        for (CastleDataMarker marker : pedestals) {
            BlockPos pos = marker.pos().below();
            ItemStack stack = getPedestalItem(level, pos);
            if (stack.isEmpty()) continue;

            clearPedestalItem(level, pos);
            level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy()));
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        ServerPlayer victim = players.get(level.getRandom().nextInt(players.size()));
        if (!(victim.level() instanceof ServerLevel victimLevel)) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(victimLevel);
        if (bolt != null) {
            bolt.moveTo(victim.getX(), victim.getY(), victim.getZ());
            bolt.setVisualOnly(true); // только эффект/звук, без урона блокам/огня
            victimLevel.addFreshEntity(bolt);
        }

        var damageTypeHolder = victimLevel.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DIVINE_PUNISHMENT);
        DamageSource divine = new DamageSource(damageTypeHolder);
        victim.hurt(divine, Float.MAX_VALUE);
    }
}
