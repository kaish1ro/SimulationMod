package com.eternity.simulation.mixin;

import divinerpg.entities.boss.EntityKitra;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code isAggressive()} (m_5912_) объявлен в базовом
 * {@code EntityDivineWaterMob}, а не в самой {@code EntityKitra} — и жёстко
 * возвращает false. От него зависит, вызовется ли {@code addAttackingAI()}
 * (регистрирует {@code MeleeAttackGoal}) в {@code registerGoals()}; раз ни
 * {@code EntityWhale}, ни {@code EntityKitra} этот метод не переопределяют,
 * ближней атаки у неё нет вообще.
 *
 * <p>Таргетим базовый класс (там метод и объявлен), но включаем агрессию
 * только для самой Китры — иначе обычные мирные киты и другие мобы на этой
 * базе тоже station стали бы кусаться. У нас уже есть compileOnly на jar
 * DivineRPG (см. build.gradle, ради KitraHurtMixin), поэтому просто
 * проверяем конкретный класс через instanceof.
 */
@Mixin(targets = "divinerpg.entities.base.EntityDivineWaterMob", remap = false)
public abstract class KitraAggressiveMixin {

    @Inject(method = "m_5912_()Z", at = @At("HEAD"), cancellable = true)
    private void simulation$kitraIsAggressive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EntityKitra) {
            cir.setReturnValue(true);
        }
    }
}
