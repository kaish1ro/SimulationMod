package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → Клиент: ответ NPC (или ошибка).
 *
 * @param response    текст ответа; пустая строка при ошибке Ollama
 * @param isError     true — ошибка (Ollama не ответила или игрок ещё ждёт)
 * @param closeAfter  true — экран должен закрыться сам через ~3 секунды после отображения
 */
public record ObserverResponsePacket(String response, boolean isError, boolean closeAfter) {

    /** Обратная совместимость: без авто-закрытия. */
    public ObserverResponsePacket(String response, boolean isError) {
        this(response, isError, false);
    }

    public static void encode(ObserverResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.response, 4096);
        buf.writeBoolean(msg.isError);
        buf.writeBoolean(msg.closeAfter);
    }

    public static ObserverResponsePacket decode(FriendlyByteBuf buf) {
        return new ObserverResponsePacket(buf.readUtf(4096), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(ObserverResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof com.eternity.simulation.screen.ObserverDialogScreen screen) {
                    screen.receiveResponse(msg.response, msg.isError, msg.closeAfter);
                }
            })
        );
        ctx.get().setPacketHandled(true);
    }
}
