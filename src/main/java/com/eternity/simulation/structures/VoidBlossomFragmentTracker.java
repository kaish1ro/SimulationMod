package com.eternity.simulation.structures;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Арена Цветка пустоты появляется НЕ в момент крафта карты, а как только у
 * игрока набралось все 4 фрагмента — так к моменту крафта площадка и её чанки
 * уже готовы, и сама выдача карты остаётся дешёвой (крафт больше не должен
 * ничего генерировать «на лету», см. {@link VoidBlossomMapCraftListener}).
 *
 * <p>Фрагменты добываются четырьмя совсем разными путями (лут в сундуке,
 * торговля у жителя, дроп головоломки, дроп босса) — единого события
 * «подобрал предмет» на всё это нет (сундук и торговля не проходят через
 * {@code EntityItemPickupEvent}). Поэтому вместо одного события просто
 * проверяем инвентарь по тику и защёлкиваем флаг «этот фрагмент у игрока
 * когда-либо был» — он не должен сбрасываться, когда фрагменты потом
 * расходуются на сам крафт.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class VoidBlossomFragmentTracker {

    private static final String ARENA_TRIGGERED_TAG = "simulation_void_blossom_arena_triggered";

    private static final RegistryObject<Item>[] FRAGMENTS = array(
            ModItems.VOID_BLOSSOM_MAP_FRAGMENT_1,
            ModItems.VOID_BLOSSOM_MAP_FRAGMENT_2,
            ModItems.VOID_BLOSSOM_MAP_FRAGMENT_3,
            ModItems.VOID_BLOSSOM_MAP_FRAGMENT_4
    );

    @SafeVarargs
    private static RegistryObject<Item>[] array(RegistryObject<Item>... items) {
        return items;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(ARENA_TRIGGERED_TAG)) return;

        boolean allCollected = true;
        for (int i = 0; i < FRAGMENTS.length; i++) {
            String flagTag = fragmentFlagTag(i);
            if (!data.getBoolean(flagTag)) {
                if (player.getInventory().countItem(FRAGMENTS[i].get()) > 0) {
                    data.putBoolean(flagTag, true);
                } else {
                    allCollected = false;
                }
            }
        }
        if (!allCollected) return;

        data.putBoolean(ARENA_TRIGGERED_TAG, true);
        spawnArena(player);
    }

    private static String fragmentFlagTag(int index) {
        return "simulation_void_blossom_fragment_" + (index + 1);
    }

    private static void spawnArena(ServerPlayer player) {
        ServerLevel undergarden = player.getServer().getLevel(VoidBlossomMapCraftListener.UNDERGARDEN_KEY);
        if (undergarden == null) return;

        BlockPos origin = VoidBlossomMapCraftListener.determineSearchOrigin(player, undergarden);
        // Результат не используется напрямую: findOrCreateArena сам кладёт
        // позицию в VoidBlossomRegistry, а VoidBlossomMapCraftListener при
        // крафте найдёт её оттуда же — тем же методом с тем же origin, только
        // это будет уже дешёвая проверка расстояний без генерации чанков.
        VoidBlossomSpawner.findOrCreateArena(undergarden, origin, player.getRandom());
    }
}
