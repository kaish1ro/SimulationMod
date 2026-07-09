package com.eternity.simulation.iceika;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import com.eternity.simulation.structures.VoidBlossomMapPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Карта башни Лича — точная копия схемы {@code VoidBlossomMapCraftListener}
 * (см. его же комментарии), только измерение — Iceika, а не Undergarden.
 * Превью карты переиспользуем: {@link VoidBlossomMapPreview} универсален,
 * ни к какому конкретному измерению не привязан.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class LichMapCraftListener {

    static final ResourceKey<Level> ICEIKA_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("divinerpg", "iceika"));

    private static final String LAST_POS_TAG = "simulation_last_iceika_pos";

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getTo().equals(ICEIKA_KEY)) return;
        Player player = event.getEntity();
        CompoundTag data = player.getPersistentData();
        data.putLong(LAST_POS_TAG, player.blockPosition().asLong());
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.getCrafting().is(ModItems.LICH_MAP.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel iceika = player.getServer().getLevel(ICEIKA_KEY);
        if (iceika == null) return;

        BlockPos origin = determineSearchOrigin(player, iceika);
        BlockPos targetPos = LichTowerSpawner.findOrCreateArena(iceika, origin, player.getRandom());
        if (targetPos == null) {
            player.sendSystemMessage(Component.literal(
                    "§8Не удалось найти место для башни поблизости — попробуй скрафтить карту ещё раз."));
            return;
        }
        IceikaAdvancements.award(player, "lich_tower");

        ItemStack mapStack = MapItem.create(iceika, targetPos.getX(), targetPos.getZ(), (byte) 2, true, true);
        VoidBlossomMapPreview.render(iceika, mapStack);
        MapItemSavedData.addTargetDecoration(mapStack, targetPos, "+", MapDecoration.Type.TARGET_X);

        ItemStack crafted = event.getCrafting();
        if (mapStack.getTag() != null) {
            crafted.setTag(mapStack.getTag().copy());
        }
        crafted.setHoverName(Component.literal("Карта башни Лича"));
    }

    static BlockPos determineSearchOrigin(ServerPlayer player, ServerLevel iceika) {
        if (player.level() == iceika) {
            return player.blockPosition();
        }
        CompoundTag data = player.getPersistentData();
        if (data.contains(LAST_POS_TAG)) {
            return BlockPos.of(data.getLong(LAST_POS_TAG));
        }
        return BlockPos.ZERO;
    }
}
