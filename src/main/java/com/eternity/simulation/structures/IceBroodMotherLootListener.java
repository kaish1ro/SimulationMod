package com.eternity.simulation.structures;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Полностью убирает ванильный/модовый дроп Ice Brood Mother и заменяет его
 * единственным фрагментом карты Ферокса №4.
 *
 * <p>Не полагаемся на переопределение лут-таблицы модa (data/threateningly_mobs/
 * loot_tables/entities/ice_brood_mother.json) как на единственный механизм —
 * порядок слияния датапаков между модами не гарантирован (другой мод теоретически
 * может загрузиться «позже» и перезаписать наш файл обратно). Событие — надёжнее.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class IceBroodMotherLootListener {

    private static final ResourceLocation ICE_BROOD_MOTHER_ID =
            new ResourceLocation("threateningly_mobs", "ice_brood_mother");

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        EntityType<?> iceBroodMother = ForgeRegistries.ENTITY_TYPES.getValue(ICE_BROOD_MOTHER_ID);
        if (iceBroodMother == null || event.getEntity().getType() != iceBroodMother) return;

        event.getDrops().clear();
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                new ItemStack(ModItems.VOID_BLOSSOM_MAP_FRAGMENT_4.get())));
    }
}
