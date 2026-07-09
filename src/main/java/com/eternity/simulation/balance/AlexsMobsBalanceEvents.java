package com.eternity.simulation.balance;

import com.eternity.simulation.SimulationMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public final class AlexsMobsBalanceEvents {
    private AlexsMobsBalanceEvents() {}

    private static final String DIMENSIONAL_CARVER = "alexsmobs:dimensional_carver";
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !DIMENSIONAL_CARVER.equals(id.toString())) return;

        replaceModifier(event, Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE_UUID, 5.0D);
        replaceModifier(event, Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED_UUID, -2.8D);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !DIMENSIONAL_CARVER.equals(id.toString())) return;
        if (!event.getFlags().isAdvanced() || !stack.isDamageableItem()) return;

        int maxDamage = stack.getMaxDamage();
        int remaining = maxDamage - stack.getDamageValue();
        event.getToolTip().add(Component.translatable("item.durability", remaining, maxDamage)
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void replaceModifier(ItemAttributeModifierEvent event,
                                        net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                        UUID uuid,
                                        double amount) {
        List<AttributeModifier> existing = new ArrayList<>(event.getModifiers().get(attribute));
        for (AttributeModifier modifier : existing) {
            event.removeModifier(attribute, modifier);
        }
        event.addModifier(attribute, new AttributeModifier(
            uuid,
            "Tool modifier",
            amount,
            AttributeModifier.Operation.ADDITION
        ));
    }
}
