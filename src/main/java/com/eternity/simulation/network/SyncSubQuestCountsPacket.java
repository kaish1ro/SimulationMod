package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Сервер → Клиент: живые счётчики мобов для подзаданий, у которых они есть
 * (первый этаж, второй этаж, волны на крыше) — {@code subQuestId -> alive count}.
 * Полный снимок, отправляется вместе с {@link SyncActiveSubQuestsPacket}.
 */
public record SyncSubQuestCountsPacket(Map<String, Integer> counts) {

    public static void encode(SyncSubQuestCountsPacket msg, FriendlyByteBuf buf) {
        buf.writeMap(msg.counts, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
    }

    public static SyncSubQuestCountsPacket decode(FriendlyByteBuf buf) {
        return new SyncSubQuestCountsPacket(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt));
    }

    public static void handle(SyncSubQuestCountsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.ClientQuestState.applySubQuestCounts(msg.counts())
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
