package com.eternity.simulation.iceika;

import com.eternity.simulation.ModItems;
import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * threateningly_mobs:ferox_iceworm — фрагмент карты №3. HP выставляем на
 * присоединении к миру (сам моб спавнится обычным ванильным спавном, через
 * биом-модификатор simulation:forge/biome_modifier/ferox_iceworm_cozybark.json,
 * а не нашим кодом — поэтому нельзя выставить атрибуты сразу при создании,
 * как у боссов, которых спавним сами).
 *
 * <p>Дроп полностью заменяем: не полагаемся на переопределение лут-таблицы
 * мода как на единственный механизм — порядок слияния датапаков между модами
 * не гарантирован (см. аналогичный комментарий в IceBroodMotherLootListener).
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class FeroxIcewormListener {

    private static final ResourceLocation FEROX_ICEWORM_ID =
            new ResourceLocation("threateningly_mobs", "ferox_iceworm");

    private static final double MAX_HEALTH = 200.0;
    private static final float FRAGMENT_DROP_CHANCE = 0.33F;

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!isFeroxIceworm(entity)) return;
        if (!(entity instanceof LivingEntity living)) return;

        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(MAX_HEALTH);
        living.setHealth((float) MAX_HEALTH);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!isFeroxIceworm(event.getEntity())) return;

        event.getDrops().clear();
        if (event.getEntity().getRandom().nextFloat() >= FRAGMENT_DROP_CHANCE) return;

        ItemStack fragment = new ItemStack(ModItems.LICH_MAP_FRAGMENT_3.get());
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), fragment));
    }

    private static boolean isFeroxIceworm(Entity entity) {
        return ForgeRegistries.ENTITY_TYPES.getValue(FEROX_ICEWORM_ID) == entity.getType();
    }
}
