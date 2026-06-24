package com.eternity.simulation.items;

import com.eternity.simulation.blocks.CastleKeyDoorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Ключ от замка с NBT-тегом {@code door_id}.
 * Название предмета и тултип показывают читаемое описание двери.
 */
public class CastleKeyItem extends Item {

    private static final Map<String, String> DOOR_NAMES = Map.of(
        "labyrinth_room_1",    "Ключ от комнаты лабиринта",
        "labyrinth_room_2",    "Ключ от комнаты лабиринта",
        "labyrinth_room_3",    "Ключ от комнаты лабиринта",
        "floor1_blue_tower",   "Ключ от синей башни",
        "floor1_exit",         "Ключ от выхода",
        "outside_tower1",      "Ключ от правой наружной башни",
        "outside_tower2",      "Ключ от левой наружной башни",
        "floor1_yellow_tower", "Ключ от желтых башен",
        "castle_roof",         "Ключ от крыши замка"
    );

    public CastleKeyItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        String desc = getDoorDescription(stack);
        return Component.literal(desc).withStyle(ChatFormatting.WHITE);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        String doorId = getDoorId(stack);
        tooltip.add(Component.literal("§8door_id: " + doorId));
    }

    private static String getDoorId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return (tag != null && tag.contains("door_id"))
            ? tag.getString("door_id")
            : CastleKeyDoorBlockEntity.DEFAULT_ID;
    }

    private static String getDoorDescription(ItemStack stack) {
        String id = getDoorId(stack);
        return DOOR_NAMES.getOrDefault(id, "Ключ замка");
    }
}
