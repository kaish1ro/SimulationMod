package com.eternity.simulation.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class LichMapFragmentItem extends Item {

    public LichMapFragmentItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Фрагмент карты, ведущей к логову Лича.")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Собери все части, чтобы узнать путь.")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
