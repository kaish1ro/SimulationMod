package com.eternity.simulation.structures;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
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
 * Карта Цветка пустоты ведёт себя как ванильная карта особняка/клада, но сама
 * арена НЕ часть обычного ворлдгена (на неё нельзя наткнуться просто
 * исследуя мир) — она появляется по требованию прямо здесь, при крафте
 * (см. {@link VoidBlossomSpawner}).
 *
 * <p>Точка поиска — позиция игрока В UNDERGARDEN. Найти «портал» программно
 * ненадёжно (это просто блоки в мире, без реестра), поэтому: если игрок прямо
 * сейчас в Undergarden — берём его текущую позицию; если нет — последнюю
 * запомненную позицию там же (см. {@link #onChangedDimension}); если он там
 * вообще не бывал — мировой ноль.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class VoidBlossomMapCraftListener {

    static final ResourceKey<Level> UNDERGARDEN_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("undergarden", "undergarden"));

    private static final String LAST_POS_TAG = "simulation_last_undergarden_pos";

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getTo().equals(UNDERGARDEN_KEY)) return;
        Player player = event.getEntity();
        CompoundTag data = player.getPersistentData();
        data.putLong(LAST_POS_TAG, player.blockPosition().asLong());
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.getCrafting().is(ModItems.VOID_BLOSSOM_MAP.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel undergarden = player.getServer().getLevel(UNDERGARDEN_KEY);
        if (undergarden == null) return;

        BlockPos origin = determineSearchOrigin(player, undergarden);
        BlockPos targetPos = VoidBlossomSpawner.findOrCreateArena(undergarden, origin, player.getRandom());
        if (targetPos == null) {
            // Не нашли подходящую точку (например, все случайные попытки попали в
            // биомы морей) — без этого сообщения предмет молча остаётся пустышкой
            // и непонятно, что вообще произошло.
            player.sendSystemMessage(Component.literal(
                    "§8Не удалось найти место для арены поблизости — попробуй скрафтить карту ещё раз."));
            return;
        }

        // unlimitedTracking=true — без него за пределами видимой области карты
        // (~1280 блоков при scale=2) маркер игрока пропадает совсем, без даже
        // стрелки-указателя направления. Ровно как у ванильной карты клада.
        ItemStack mapStack = MapItem.create(undergarden, targetPos.getX(), targetPos.getZ(), (byte) 2, true, true);

        // Превью — через биомы из шума генератора, БЕЗ форс-генерации чанков
        // (ванильный renderBiomePreviewMap на нетронутой местности генерировал
        // до ~4000 чанков синхронно — карта из-за этого приходила пустой).
        VoidBlossomMapPreview.render(undergarden, mapStack);

        // Крест цели addTargetDecoration пишет в NBT самого стека (тег
        // "Decorations"), а не в серверные данные карты — поэтому строго ДО
        // копирования тега в crafted, иначе крест остаётся во временном стеке.
        MapItemSavedData.addTargetDecoration(mapStack, targetPos, "+", MapDecoration.Type.TARGET_X);

        ItemStack crafted = event.getCrafting();
        if (mapStack.getTag() != null) {
            crafted.setTag(mapStack.getTag().copy());
        }
        crafted.setHoverName(Component.literal("Карта Цветка пустоты"));
    }

    static BlockPos determineSearchOrigin(ServerPlayer player, ServerLevel undergarden) {
        if (player.level() == undergarden) {
            return player.blockPosition();
        }
        CompoundTag data = player.getPersistentData();
        if (data.contains(LAST_POS_TAG)) {
            return BlockPos.of(data.getLong(LAST_POS_TAG));
        }
        return BlockPos.ZERO;
    }
}
