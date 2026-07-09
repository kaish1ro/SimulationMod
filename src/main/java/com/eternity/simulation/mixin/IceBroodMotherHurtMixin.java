package com.eternity.simulation.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * threateningly_mobs:ice_brood_mother переопределяет hurt() и ПЕРВЫМ ДЕЛОМ —
 * до любых проверок типа урона и до вызова super.hurt() (где обычно проверяется
 * invulnerable) — безусловно спавнит ice_weaver. Поэтому Invulnerable:1b сам по
 * себе не спасает: спавн уже произошёл до того, как инвулнерабилити вообще
 * успевает сработать (см. декомпиляцию байткода — вызов
 * IceBroodMotherspawnbabyProcedure.execute(...) идёт раньше всех остальных
 * инструкций метода).
 *
 * <p>Цель задаётся СТРОКОЙ (без compile-зависимости от мода) и SRG-именем
 * метода напрямую (m_6469_ — таков он в скомпилированном джаре мода; мы это
 * проверили через javap, поэтому ремап через refmap не нужен и не сработал бы
 * корректно для метода чужого мода).
 */
@Mixin(targets = "net.mcreator.threateninglymobs.entity.IceBroodMotherEntity", remap = false)
public abstract class IceBroodMotherHurtMixin {

    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void simulation$blockHurtWhileFrozen(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getPersistentData().getBoolean("simulation_frozen")) {
            cir.setReturnValue(false);
        }
    }
}
