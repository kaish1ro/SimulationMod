package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Сервер → Клиент: список id выполненных квестов Симуляции (полный снимок, не дельта). */
public record SyncQuestStatePacket(List<String> completedQuestIds) {

    public static void encode(SyncQuestStatePacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.completedQuestIds, FriendlyByteBuf::writeUtf);
    }

    public static SyncQuestStatePacket decode(FriendlyByteBuf buf) {
        return new SyncQuestStatePacket(buf.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(SyncQuestStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.ClientQuestState.applySync(msg.completedQuestIds())
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
