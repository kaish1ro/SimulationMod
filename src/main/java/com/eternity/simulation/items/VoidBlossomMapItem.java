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
 * Полноценная карта (как ванильная карта особняка/клада) — функциональность
 * (открытие на экране, зум, иконка цели) наследуется от {@code MapItem}
 * напрямую. Своя текстура задаётся в item-модели (не через override predicate
 * уровня зума, как у ваниль­ной filled_map — просто наш статичный значок).
 *
 * <p>До крафта (просто из реестра/креатива, без NBT карты) ведёт себя как
 * обычный предмет без функции — рабочей она становится только после того,
 * как {@code VoidBlossomMapCraftListener} прикрепит данные карты при крафте.
 */
public class VoidBlossomMapItem extends MapItem {

    public VoidBlossomMapItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Все четыре фрагмента сложились в единую карту.")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Путь к Цветку пустоты теперь известен.")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
