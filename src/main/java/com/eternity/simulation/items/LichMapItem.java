package com.eternity.simulation.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Полноценная карта (как ванильная карта особняка/клада) — см.
 * {@link VoidBlossomMapItem}, тот же принцип. Рабочей становится только
 * после того, как {@code LichMapCraftListener} прикрепит данные карты при
 * крафте из 4 фрагментов.
 */
public class LichMapItem extends MapItem {

    public LichMapItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Все четыре фрагмента сложились в единую карту.")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Путь к башне Лича теперь известен.")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
