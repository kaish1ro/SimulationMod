package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер → Клиент: закрыть экран загрузки замка — перестройка завершена. */
public record CloseCastleLoadingScreenPacket() {

    public static void encode(CloseCastleLoadingScreenPacket msg, FriendlyByteBuf buf) {}

    public static CloseCastleLoadingScreenPacket decode(FriendlyByteBuf buf) {
        return new CloseCastleLoadingScreenPacket();
    }

    public static void handle(CloseCastleLoadingScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.CastleLoadingScreen.closeOnClient()
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
