package com.eternity.simulation.mixin;

import com.eternity.simulation.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * divinerpg:workshop_merchant ("Торговец мастерской") строит свой список
 * предложений полностью в коде — {@code updateTrades()} (m_7604_) собирает
 * пул из 20 {@code DivineTrades} и вызывает ванильный
 * {@code Villager.addOffersFromItemListings(offers, pool, 5)}, который
 * случайно выбирает 5 штук за раз. У этой сущности нет реальных ванильных
 * уровней торговли (Novice/Apprentice/...) — единый плоский пул. Поэтому
 * "второй уровень торговли" реализован просто как ещё одно предложение,
 * которое добавляется в его {@code MerchantOffers} после стандартной
 * генерации — оно не выбирается случайно из пула, а гарантированно
 * присутствует всегда.
 */
@Mixin(targets = "divinerpg.entities.iceika.EntityWorkshopMerchant", remap = false)
public abstract class WorkshopMerchantTradeMixin {

    private static final ResourceLocation OLIVINE_ID = new ResourceLocation("divinerpg", "olivine");

    @Inject(method = "m_7604_()V", at = @At("TAIL"))
    private void simulation$addFragmentTrade(CallbackInfo ci) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        MerchantOffers offers = self.getOffers();

        ItemStack fragment = new ItemStack(ModItems.LICH_MAP_FRAGMENT_2.get());
        boolean alreadyOffered = offers.stream().anyMatch(offer -> offer.getResult().is(fragment.getItem()));
        if (alreadyOffered) return;

        ItemStack olivine = new ItemStack(ForgeRegistries.ITEMS.getValue(OLIVINE_ID), 10);
        offers.add(new MerchantOffer(olivine, fragment, 1, 30, 0.05F));
    }
}
