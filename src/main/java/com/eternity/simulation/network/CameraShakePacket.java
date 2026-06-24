package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → Клиент: встряхнуть камеру с заданной интенсивностью на durationMs миллисекунд. */
public record CameraShakePacket(float intensity, int durationMs) {

    public static void encode(CameraShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeInt(msg.durationMs);
    }

    public static CameraShakePacket decode(FriendlyByteBuf buf) {
        return new CameraShakePacket(buf.readFloat(), buf.readInt());
    }

    public static void handle(CameraShakePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.CameraEffectHandler.startShake(msg.intensity, msg.durationMs)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
