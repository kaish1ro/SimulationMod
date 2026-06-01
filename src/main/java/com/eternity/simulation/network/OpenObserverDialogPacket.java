package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер → Клиент: открыть диалоговый экран.
 *
 * @param autoFirstMessage  если true — экран автоматически отправит «О чём?» при открытии
 *                          (используется для первого диалога со Скитальцем)
 */
public record OpenObserverDialogPacket(UUID observerUuid, String npcName, boolean autoFirstMessage) {

    /** Обратная совместимость: без авто-сообщения (для Наблюдателя). */
    public OpenObserverDialogPacket(UUID observerUuid, String npcName) {
        this(observerUuid, npcName, false);
    }

    public static void encode(OpenObserverDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.observerUuid);
        buf.writeUtf(msg.npcName);
        buf.writeBoolean(msg.autoFirstMessage);
    }

    public static OpenObserverDialogPacket decode(FriendlyByteBuf buf) {
        return new OpenObserverDialogPacket(buf.readUUID(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(OpenObserverDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.eternity.simulation.screen.ObserverDialogScreen(
                        msg.observerUuid, msg.npcName, msg.autoFirstMessage)
                )
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
