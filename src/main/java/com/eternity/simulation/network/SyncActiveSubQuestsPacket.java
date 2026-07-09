package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер → Клиент: список id активных промежуточных заданий
 * ({@code com.eternity.simulation.quests.SubQuestRegistry}). Полный снимок, не дельта.
 * Метаданные (title/parentQuestId) клиент берёт из своей копии реестра — по
 * сети едут только id.
 */
public record SyncActiveSubQuestsPacket(List<String> activeSubQuestIds) {

    public static void encode(SyncActiveSubQuestsPacket msg, FriendlyByteBuf buf) {
        buf.writeCollection(msg.activeSubQuestIds, FriendlyByteBuf::writeUtf);
    }

    public static SyncActiveSubQuestsPacket decode(FriendlyByteBuf buf) {
        return new SyncActiveSubQuestsPacket(buf.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(SyncActiveSubQuestsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.ClientQuestState.applySubQuestSync(msg.activeSubQuestIds())
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
