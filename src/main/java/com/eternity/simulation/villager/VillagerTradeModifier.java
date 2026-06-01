package com.eternity.simulation.villager;

import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Балансировочные изменения торговли жителей.
 *
 * <p>Библиотекарь:
 * <ul>
 *   <li>Mending полностью убирается.</li>
 *   <li>Protection, Sharpness, Power, Efficiency капаются на уровне ниже макс.</li>
 * </ul>
 * Фермер ×5, Оружейник ×4, Флетчер ×3, Клирик ×3.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VillagerTradeModifier {

    private static final Logger LOGGER = LogManager.getLogger("simulation.Trades");

    // Чары полностью убранные из библиотекаря
    // Включает Mending (ванильный) и все зачарования Cyclic (имбалансные)
    private static final Set<ResourceLocation> ENCHANT_BLACKLIST = Set.of(
            new ResourceLocation("mending"),
            // Cyclic enchantments — убраны полностью
            new ResourceLocation("cyclic", "laststand"),
            new ResourceLocation("cyclic", "excavate"),
            new ResourceLocation("cyclic", "multishot"),
            new ResourceLocation("cyclic", "beheading"),
            new ResourceLocation("cyclic", "experience_boost"),
            new ResourceLocation("cyclic", "growth"),
            new ResourceLocation("cyclic", "launch"),
            new ResourceLocation("cyclic", "life_leech"),
            new ResourceLocation("cyclic", "magnet"),
            new ResourceLocation("cyclic", "quickshot"),
            new ResourceLocation("cyclic", "reach"),
            new ResourceLocation("cyclic", "step"),
            new ResourceLocation("cyclic", "traveler"),
            new ResourceLocation("cyclic", "venom"),
            new ResourceLocation("cyclic", "steady"),
            new ResourceLocation("cyclic", "auto_smelt"),
            new ResourceLocation("cyclic", "disarm"),
            new ResourceLocation("cyclic", "curse"),
            new ResourceLocation("cyclic", "ender"),
            new ResourceLocation("cyclic", "beekeeper")
    );

    // Чары с пониженным максимальным уровнем (ключ → новый макс)
    private static final Map<ResourceLocation, Integer> ENCHANT_MAX_LEVEL = Map.of(
            new ResourceLocation("protection"), 3,
            new ResourceLocation("sharpness"),  4,
            new ResourceLocation("power"),       4,
            new ResourceLocation("efficiency"),  4
    );

    // Доступ к пакет-приватному классу через рефлексию
    @SuppressWarnings("rawtypes")
    private static final Class ENCHANT_BOOK_CLASS;
    private static final Field XP_FIELD;

    static {
        Class<?> cls = null;
        Field xpField = null;
        try {
            cls = Class.forName("net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds");
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == int.class) { // поле xp
                    f.setAccessible(true);
                    xpField = f;
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Trades] Could not reflect EnchantBookForEmeralds: {}", e.getMessage());
        }
        ENCHANT_BOOK_CLASS = cls;
        XP_FIELD = xpField;
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() == VillagerProfession.LIBRARIAN) {
            fixLibrarian(event);
        } else if (event.getType() == VillagerProfession.FARMER) {
            wrapPrices(event, 5f,
                    new ResourceLocation("minecraft", "bread"),
                    new ResourceLocation("minecraft", "cookie"));
        } else if (event.getType() == VillagerProfession.WEAPONSMITH) {
            wrapPrices(event, 4f,
                    new ResourceLocation("minecraft", "diamond_sword"),
                    new ResourceLocation("minecraft", "diamond_axe"));
        } else if (event.getType() == VillagerProfession.FLETCHER) {
            wrapPrices(event, 3f,
                    new ResourceLocation("minecraft", "arrow"),
                    new ResourceLocation("minecraft", "tipped_arrow"));
        } else if (event.getType() == VillagerProfession.CLERIC) {
            wrapPrices(event, 3f,
                    new ResourceLocation("minecraft", "potion"),
                    new ResourceLocation("minecraft", "splash_potion"));
        }
    }

    // ── Библиотекарь ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void fixLibrarian(VillagerTradesEvent event) {
        if (ENCHANT_BOOK_CLASS == null) return;
        int replaced = 0;
        for (List<VillagerTrades.ItemListing> tier : event.getTrades().values()) {
            List<VillagerTrades.ItemListing> toAdd = new ArrayList<>();
            var iter = tier.iterator();
            while (iter.hasNext()) {
                VillagerTrades.ItemListing listing = iter.next();
                if (ENCHANT_BOOK_CLASS.isInstance(listing)) {
                    int xp = getXp(listing);
                    iter.remove();
                    toAdd.add(new FilteredEnchantBook(xp));
                    replaced++;
                }
            }
            tier.addAll(toAdd);
        }
        LOGGER.info("[Trades] Librarian: replaced {} book listings.", replaced);
    }

    private static int getXp(VillagerTrades.ItemListing listing) {
        if (XP_FIELD == null) return 1;
        try {
            return (int) XP_FIELD.get(listing);
        } catch (Exception e) {
            return 1;
        }
    }

    // ── Фильтрованная книга ───────────────────────────────────────────────────

    private record FilteredEnchantBook(int xp) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            List<Enchantment> pool = ForgeRegistries.ENCHANTMENTS.getValues().stream()
                    .filter(e -> e.isDiscoverable() && !isBlacklisted(e))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (pool.isEmpty()) return null;

            Enchantment ench = pool.get(random.nextInt(pool.size()));
            int maxLvl = getCapLevel(ench);
            int level  = Mth.nextInt(random, ench.getMinLevel(),
                    Math.min(ench.getMaxLevel(), maxLvl));

            ItemStack book = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(ench, level));

            int cost = 2 + random.nextInt(5 + level * 10) + 3 * level;
            cost = Math.min(cost, 64);

            return new MerchantOffer(
                    new ItemStack(Items.BOOK),
                    new ItemStack(Items.EMERALD, cost),
                    book, 12, xp, 0.2f);
        }

        private static boolean isBlacklisted(Enchantment e) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e);
            return id != null && ENCHANT_BLACKLIST.contains(id);
        }

        private static int getCapLevel(Enchantment e) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e);
            return id != null ? ENCHANT_MAX_LEVEL.getOrDefault(id, e.getMaxLevel()) : e.getMaxLevel();
        }
    }

    // ── Умножение цены ────────────────────────────────────────────────────────

    private static void wrapPrices(VillagerTradesEvent event, float mult, ResourceLocation... items) {
        Set<ResourceLocation> targets = Set.of(items);
        for (List<VillagerTrades.ItemListing> tier : event.getTrades().values()) {
            tier.replaceAll(l -> new PriceMultiplied(l, mult, targets));
        }
    }

    private record PriceMultiplied(
            VillagerTrades.ItemListing original,
            float multiplier,
            Set<ResourceLocation> targets
    ) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            MerchantOffer offer = original.getOffer(entity, random);
            if (offer == null) return null;

            ResourceLocation resultId = ForgeRegistries.ITEMS.getKey(offer.getResult().getItem());
            if (resultId == null || !targets.contains(resultId)) return offer;

            ItemStack costA = offer.getCostA().copy();
            costA.setCount(Math.min(64, Math.max(1, Math.round(costA.getCount() * multiplier))));
            ItemStack costB = offer.getCostB().copy();

            return new MerchantOffer(
                    costA,
                    costB.isEmpty() ? ItemStack.EMPTY : costB,
                    offer.getResult().copy(),
                    offer.getMaxUses(),
                    offer.getXp(),
                    offer.getPriceMultiplier());
        }
    }
}
