package com.eternity.simulation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IceBroodMotherEntity.die() вызывает super.die() (ванильный дроп — его и
 * перехватывает LivingDropsEvent), А ПОТОМ отдельно зовёт
 * IceBroodMotherDeadsetProcedure.execute(...), которая НАПРЯМУЮ создаёт
 * {@code ItemEntity} для threateningly_mobs:ice_trial_proving и
 * threateningly_mobs:treasureroomkey и добавляет их в мир — мимо
 * loot-таблицы и мимо LivingDropsEvent (типичный для MCreator-генерируемых
 * модов паттерн «множественный дроп» — см. декомпиляцию байткода). Поэтому
 * ни переопределение лут-таблицы, ни LivingDropsEvent эти предметы не ловят —
 * глушим саму процедуру целиком (она же выдаёт достижение мода, тоже не нужное).
 */
@Mixin(targets = "net.mcreator.threateninglymobs.procedures.IceBroodMotherDeadsetProcedure", remap = false)
public abstract class IceBroodMotherDeadsetMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void simulation$cancelExtraDrops(LevelAccessor level, double x, double y, double z,
                                                     Entity source, CallbackInfo ci) {
        ci.cancel();
    }
}
