package com.eternity.simulation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → Клиент: можно ли сейчас показывать квестовое меню/HUD.
 * {@code false} до первого попадания игрока в перестроенный замок (см.
 * {@code SimulationSavedData.isCastleEverEntered}), и снова {@code false}
 * после того как поле снято и игрок отошёл дальше 100 блоков от маяка.
 */
public record SyncQuestUiVisibilityPacket(boolean visible) {

    public static void encode(SyncQuestUiVisibilityPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.visible);
    }

    public static SyncQuestUiVisibilityPacket decode(FriendlyByteBuf buf) {
        return new SyncQuestUiVisibilityPacket(buf.readBoolean());
    }

    public static void handle(SyncQuestUiVisibilityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.eternity.simulation.client.ClientQuestState.applyUiVisibility(msg.visible())
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
