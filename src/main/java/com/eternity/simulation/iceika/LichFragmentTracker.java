package com.eternity.simulation.iceika;

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
 * Точная копия схемы {@code VoidBlossomFragmentTracker} (см. его же
 * комментарии) — башня Лича появляется, как только у игрока набрались все
 * 4 фрагмента карты, а не в момент самого крафта.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class LichFragmentTracker {

    private static final String ARENA_TRIGGERED_TAG = "simulation_lich_tower_triggered";

    private static final RegistryObject<Item>[] FRAGMENTS = array(
            ModItems.LICH_MAP_FRAGMENT_1,
            ModItems.LICH_MAP_FRAGMENT_2,
            ModItems.LICH_MAP_FRAGMENT_3,
            ModItems.LICH_MAP_FRAGMENT_4
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
        spawnTower(player);
    }

    private static String fragmentFlagTag(int index) {
        return "simulation_lich_fragment_" + (index + 1);
    }

    private static void spawnTower(ServerPlayer player) {
        ServerLevel iceika = player.getServer().getLevel(LichMapCraftListener.ICEIKA_KEY);
        if (iceika == null) return;

        BlockPos origin = LichMapCraftListener.determineSearchOrigin(player, iceika);
        BlockPos tower = LichTowerSpawner.findOrCreateArena(iceika, origin, player.getRandom());
        if (tower != null) {
            IceikaAdvancements.award(player, "lich_tower");
        }
    }
}
