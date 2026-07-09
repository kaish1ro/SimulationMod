package com.eternity.simulation.iceika;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** divinerpg:kitra — фрагмент карты №4, добавляется поверх её обычного дропа. */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class KitraDropListener {

    private static final ResourceLocation KITRA_ID = new ResourceLocation("divinerpg", "kitra");

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (ForgeRegistries.ENTITY_TYPES.getValue(KITRA_ID) != event.getEntity().getType()) return;

        ItemStack fragment = new ItemStack(ModItems.LICH_MAP_FRAGMENT_4.get());
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), fragment));
    }
}
