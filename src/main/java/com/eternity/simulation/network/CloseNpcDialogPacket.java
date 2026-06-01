package com.eternity.simulation.network;

import com.eternity.simulation.entity.WandererDialogHandler;
import com.eternity.simulation.entity.WandererEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Клиент → Сервер: игрок закрыл диалоговый экран.
 * Нужно чтобы WandererEntity разморозился и начал прощаться.
 */
public record CloseNpcDialogPacket(UUID npcUuid) {

    public static void encode(CloseNpcDialogPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.npcUuid);
    }

    public static CloseNpcDialogPacket decode(FriendlyByteBuf buf) {
        return new CloseNpcDialogPacket(buf.readUUID());
    }

    public static void handle(CloseNpcDialogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(msg.npcUuid);
            if (entity instanceof WandererEntity wanderer) {
                wanderer.onDialogClose();
                // Чистим сессию — игрок закрыл экран вручную
                WandererDialogHandler.INSTANCE.onDialogClosed(player.getUUID());
            }
            // Для Observer — ничего не нужно
        });
        ctx.get().setPacketHandled(true);
    }
}
