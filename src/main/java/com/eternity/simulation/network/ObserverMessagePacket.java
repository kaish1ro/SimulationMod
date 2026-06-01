package com.eternity.simulation.network;

import com.eternity.simulation.entity.ObserverDialogHandler;
import com.eternity.simulation.entity.ObserverEntity;
import com.eternity.simulation.entity.WandererDialogHandler;
import com.eternity.simulation.entity.WandererEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Клиент → Сервер: игрок отправил сообщение NPC.
 * Маршрутизация на сервере по типу сущности.
 */
public record ObserverMessagePacket(UUID npcUuid, String message) {

    public static void encode(ObserverMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.npcUuid);
        buf.writeUtf(msg.message, 500);
    }

    public static ObserverMessagePacket decode(FriendlyByteBuf buf) {
        return new ObserverMessagePacket(buf.readUUID(), buf.readUtf(500));
    }

    public static void handle(ObserverMessagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.serverLevel().getEntity(msg.npcUuid);

            if (entity instanceof ObserverEntity) {
                ObserverDialogHandler.INSTANCE.handleMessage(player, msg.npcUuid, msg.message);
            } else if (entity instanceof WandererEntity) {
                WandererDialogHandler.INSTANCE.handleMessage(player, msg.npcUuid, msg.message);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
