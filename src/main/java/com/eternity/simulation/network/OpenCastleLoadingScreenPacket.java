package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → Клиент: открыть экран загрузки замка (имитация перехода между мирами,
 * пока {@link com.eternity.simulation.castle.CastleInterceptTask} перестраивает Final Castle).
 * Без полезной нагрузки — экран статичен на клиенте.
 */
public record OpenCastleLoadingScreenPacket() {

    public static void encode(OpenCastleLoadingScreenPacket msg, FriendlyByteBuf buf) {}

    public static OpenCastleLoadingScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenCastleLoadingScreenPacket();
    }

    public static void handle(OpenCastleLoadingScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.CastleLoadingScreen.openOnClient()
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
