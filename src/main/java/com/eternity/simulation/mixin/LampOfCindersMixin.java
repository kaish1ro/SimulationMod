package com.eternity.simulation.mixin;

import com.eternity.simulation.castle.CastleInterceptTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * По задумке разработчиков TF игрок сжигает шипы Лампой Углей, чтобы попасть
 * к Final Castle. У лампы ДВА независимых режима сжигания, оба в итоге зовут
 * приватный {@code burnBlock(Level, BlockPos)}:
 * <ul>
 *   <li>ПКМ по блоку шипов — {@code useOn} (m_6225_), одноблочное сжигание;</li>
 *   <li>ПКМ в воздухе (удержание) — {@code use} (m_7203_) запускает зарядку, дальше
 *       каждый тик зовётся {@code onUseTick} (m_5551_) → {@code doBurnEffect}, который
 *       жжёт шипы в кубе 9×9×9 вокруг игрока — было упущено раньше, ловился только
 *       первый режим.</li>
 * </ul>
 * Ловим оба варианта через общую точку {@code burnBlock} (единственное место, которое
 * ДОСТОВЕРНО знает, сгорел ли блок реально — подтверждено декомпиляцией байткода),
 * а игрока-инициатора запоминаем в момент входа в {@code useOn}/{@code doBurnEffect}
 * (оба метода получают его как параметр, сам {@code burnBlock} — нет).
 */
@Mixin(targets = "twilightforest.item.LampOfCindersItem", remap = false)
public abstract class LampOfCindersMixin {

    /** Кто сейчас жжёт — выставляется на входе в useOn/doBurnEffect, читается в burnBlock. */
    private static Player simulation$currentUser;

    @Inject(method = "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void simulation$captureUseOnPlayer(UseOnContext context, CallbackInfoReturnable<?> cir) {
        simulation$currentUser = context.getPlayer();
    }

    @Inject(method = "doBurnEffect(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"))
    private void simulation$captureAoeUser(Level level, LivingEntity entity, CallbackInfo ci) {
        simulation$currentUser = entity instanceof Player p ? p : null;
    }

    @Inject(method = "burnBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"))
    private void simulation$onThornBurned(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (simulation$currentUser instanceof ServerPlayer serverPlayer) {
            CastleInterceptTask.onThornBurned(serverPlayer);
        }
    }
}
