package com.eternity.simulation.mixin;

import com.eternity.simulation.SimulationMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Квест "Порог открыт" (FTB Quests, chapter4) висел на достижении
 * {@code simulation:end_portal_activated}, чей критерий ссылался на несуществующий
 * триггер {@code endrem:make_portal} — у EnderRemastered такого триггера нет вообще
 * (проверено декомпиляцией: мод нигде не регистрирует свой AdvancementTrigger,
 * все его собственные достижения используют только ванильный
 * {@code minecraft:inventory_changed}). Достижение с несуществующим триггером
 * никогда не выполнялось — квест и вся цепочка после него были непроходимы.
 *
 * <p>EREnderEye.useOn (m_6225_) сам, без каких-либо ивентов, заполняет портал
 * блоками {@code minecraft:end_portal} при полной активации рамки — сразу
 * ПОСЛЕ этого зовёт {@code Level.globalLevelEvent(1038, pos, 0)} (звук
 * "портал сформирован"), и это единственное надёжное место перехвата: строка
 * не встречается больше нигде в методе, поэтому инъекция однозначна.
 * Достижение выдаём напрямую через {@code PlayerAdvancements.award(...)},
 * минуя систему триггеров — критерий в JSON теперь {@code minecraft:impossible}
 * (штатный ванильный триггер именно для достижений, выдаваемых кодом/командой).
 */
@Mixin(targets = "com.teamremastered.endrem.items.EREnderEye", remap = false)
public abstract class EREnderEyeMixin {

    @Inject(method = "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6798_(ILnet/minecraft/core/BlockPos;I)V"))
    private void simulation$onPortalFormed(UseOnContext context, CallbackInfoReturnable<?> cir) {
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Advancement advancement = serverPlayer.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation(SimulationMod.MODID, "end_portal_activated"));
        if (advancement != null) {
            serverPlayer.getAdvancements().award(advancement, "activate");
        }
    }
}
