package com.eternity.simulation.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ваниль решает, рисовать ли карту «в руках» (вместо обычного предмета), через
 * жёсткую проверку {@code stack.is(Items.FILLED_MAP)} внутри
 * {@code renderArmWithItem} — НЕ через {@code instanceof MapItem}. Поэтому наш
 * {@code VoidBlossomMapItem extends MapItem} с настоящими данными карты в NBT
 * всё равно рисовался как обычный плоский предмет, а не как раскрытая карта —
 * именно это и не давало «карте открыться». Расширяем единственную проверку
 * этого вида в методе (подтверждено — других {@code .is(...)} там нет) на
 * любой {@code MapItem}, не только ваниль­ный.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Redirect(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean simulation$treatCustomMapsAsFilledMap(ItemStack stack, Item item) {
        return stack.is(item) || stack.getItem() instanceof MapItem;
    }
}
