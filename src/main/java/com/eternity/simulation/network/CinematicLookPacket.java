package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → Клиент: направить камеру на мировую точку (targetX, targetY, targetZ) на durationMs мс. */
public record CinematicLookPacket(double targetX, double targetY, double targetZ, int durationMs) {

    public static void encode(CinematicLookPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.targetX);
        buf.writeDouble(msg.targetY);
        buf.writeDouble(msg.targetZ);
        buf.writeInt(msg.durationMs);
    }

    public static CinematicLookPacket decode(FriendlyByteBuf buf) {
        return new CinematicLookPacket(
            buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    public static void handle(CinematicLookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.CameraEffectHandler.startLook(
                    msg.targetX, msg.targetY, msg.targetZ, msg.durationMs)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
