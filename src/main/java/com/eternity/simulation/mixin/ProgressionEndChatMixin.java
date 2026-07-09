package com.eternity.simulation.mixin;

import net.minecraftforge.event.entity.player.AdvancementEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TF сам рассылает игроку чат-сообщение "gui.twilightforest.progression_end.message"
 * (со ссылкой на Discord) на {@code AdvancementEvent.AdvancementEarnEvent} для
 * {@code progression_end} — отдельно от вантльного announce_to_chat/toast (у нас он
 * и так выключен в переопределённом display, см.
 * {@code data/twilightforest/advancements/progression_end.json}). Само достижение
 * переиспользовано под нашу прогрессию (см. {@code PlayerAdvancementsMixin}), а это
 * сообщение про "недоделанный замок в разработке" тут не к месту — глушим целиком.
 */
@Mixin(targets = "twilightforest.events.EntityEvents", remap = false)
public abstract class ProgressionEndChatMixin {

    @Inject(method = "alertPlayerCastleIsWIP(Lnet/minecraftforge/event/entity/player/AdvancementEvent$AdvancementEarnEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private static void simulation$suppressCastleWipChat(AdvancementEvent.AdvancementEarnEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
