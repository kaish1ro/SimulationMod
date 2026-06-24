package com.eternity.simulation;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

@Mod.EventBusSubscriber(modid = SimulationMod.MODID)
public class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
        DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SimulationMod.MODID);

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
        DeferredRegister.create(Registries.VILLAGER_PROFESSION, SimulationMod.MODID);

    private static final ResourceKey<PoiType> HERBALISTS_TABLE_KEY =
        ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            new ResourceLocation(SimulationMod.MODID, "herbalists_table"));

    public static final RegistryObject<PoiType> HERBALISTS_TABLE_POI = POI_TYPES.register(
        "herbalists_table",
        () -> new PoiType(
            ImmutableSet.copyOf(ModBlocks.HERBALISTS_TABLE.get().getStateDefinition().getPossibleStates()),
            1, 1
        )
    );

    public static final RegistryObject<VillagerProfession> STRANGE_HERBALIST = PROFESSIONS.register(
        "strange_herbalist",
        () -> new VillagerProfession(
            "strange_herbalist",
            holder -> holder.is(HERBALISTS_TABLE_KEY),
            holder -> holder.is(HERBALISTS_TABLE_KEY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_FLETCHER
        )
    );

    // ── Торговля ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!event.getType().equals(STRANGE_HERBALIST.get())) return;

        Item cloggrum   = ugItem("cloggrum_ingot");
        Item froststeel = ugItem("froststeel_ingot");
        Item uthreium   = ugItem("uthreium_crystal");
        Item forgotten  = ugItem("forgotten_ingot");

        if (cloggrum == null || froststeel == null || uthreium == null || forgotten == null) return;

        List<VillagerTrades.ItemListing> tier1 = event.getTrades().get(1);
        // Растения и грибы Undergarden — игрок платит слитками, получает растения
        tier1.add(trade(cloggrum,   1, "deepturf",          5, 6,  12, 5));
        tier1.add(trade(cloggrum,   1, "blood_mushroom",    3, 4,  10, 5));
        tier1.add(trade(cloggrum,   1, "ink_mushroom",      3, 4,  10, 5));
        tier1.add(trade(froststeel, 1, "indigo_mushroom",   3, 4,  10, 5));
        tier1.add(trade(froststeel, 1, "ditchbulb",         4, 6,  12, 5));
        tier1.add(trade(cloggrum,   1, "mogmoss",           4, 6,  12, 5));
        tier1.add(trade(froststeel, 1, "blue_mogmoss",      4, 6,  12, 5));
        tier1.add(trade(uthreium,   1, "underbeans",        3, 5,  10, 5));
        tier1.add(trade(uthreium,   1, "gloomgourd_seeds",  4, 6,  10, 5));

        List<VillagerTrades.ItemListing> tier2 = event.getTrades().get(2);
        // Кусок карты — редкая сделка 2-го уровня за forgotten_ingot
        tier2.add((trader, rand) -> {
            int cost = 10 + rand.nextInt(3); // 10, 11 или 12
            return new MerchantOffer(
                new ItemStack(forgotten, cost),
                new ItemStack(ModItems.FEROX_MAP_FRAGMENT_2.get()),
                1, 30, 0.05f
            );
        });
    }

    /** Формирует сделку: оплата costItem × costCount → resultMin..resultMax штук товара из Undergarden */
    private static VillagerTrades.ItemListing trade(
            Item costItem, int costCount,
            String ugResultName, int resultMin, int resultMax,
            int maxUses, int xp) {
        return (trader, rand) -> {
            Item result = ugItem(ugResultName);
            if (result == null) return null;
            int count = resultMin == resultMax ? resultMin
                        : resultMin + rand.nextInt(resultMax - resultMin + 1);
            return new MerchantOffer(
                new ItemStack(costItem, costCount),
                new ItemStack(result, count),
                maxUses, xp, 0.0f
            );
        };
    }

    private static Item ugItem(String name) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("undergarden", name));
    }
}
