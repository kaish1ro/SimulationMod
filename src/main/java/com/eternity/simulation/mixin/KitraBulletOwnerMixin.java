package com.eternity.simulation.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Костяная бомба Китры при попадании (onHit/m_6532_) разлетается на до 64
 * костяных осколков, каждый из которых конструируется с {@code owner =
 * this.getOwner()}. Если Китра к моменту попадания снаряда уже мертва и
 * убрана из мира (getOwner() возвращает null), конструктор осколка
 * (в конечном счёте — ванильный {@code ThrowableProjectile}) падает с NPE:
 * он безусловно читает координаты владельца. Раньше это не проявлялось,
 * потому что 750 HP по умолчанию редко кончались, пока снаряд ещё летит —
 * с уменьшенным до 300 HP она чаще умирает, не успев "доиграть" уже
 * выпущенные снаряды.
 *
 * <p>Если владельца уже нет — просто убираем снаряд без взрыва на осколки
 * (сам взрыв — декоративный бонус-эффект мода, не критичная механика).
 */
@Mixin(targets = "divinerpg.entities.boss.EntityKitra$1", remap = false)
public abstract class KitraBulletOwnerMixin {

    @Inject(method = "m_6532_(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"), cancellable = true)
    private void simulation$skipFragmentsIfOwnerGone(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.getOwner() == null) {
            self.kill();
            ci.cancel();
        }
    }
}
