package com.eternity.simulation.network;

import com.eternity.simulation.SimulationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(SimulationMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++,
            OpenObserverDialogPacket.class,
            OpenObserverDialogPacket::encode,
            OpenObserverDialogPacket::decode,
            OpenObserverDialogPacket::handle);

        CHANNEL.registerMessage(nextId++,
            ObserverMessagePacket.class,
            ObserverMessagePacket::encode,
            ObserverMessagePacket::decode,
            ObserverMessagePacket::handle);

        CHANNEL.registerMessage(nextId++,
            ObserverResponsePacket.class,
            ObserverResponsePacket::encode,
            ObserverResponsePacket::decode,
            ObserverResponsePacket::handle);

        CHANNEL.registerMessage(nextId++,
            CloseNpcDialogPacket.class,
            CloseNpcDialogPacket::encode,
            CloseNpcDialogPacket::decode,
            CloseNpcDialogPacket::handle);

        CHANNEL.registerMessage(nextId++,
            CameraShakePacket.class,
            CameraShakePacket::encode,
            CameraShakePacket::decode,
            CameraShakePacket::handle);

        CHANNEL.registerMessage(nextId++,
            CinematicLookPacket.class,
            CinematicLookPacket::encode,
            CinematicLookPacket::decode,
            CinematicLookPacket::handle);

        CHANNEL.registerMessage(nextId++,
            SyncQuestStatePacket.class,
            SyncQuestStatePacket::encode,
            SyncQuestStatePacket::decode,
            SyncQuestStatePacket::handle);

        CHANNEL.registerMessage(nextId++,
            SyncActiveSubQuestsPacket.class,
            SyncActiveSubQuestsPacket::encode,
            SyncActiveSubQuestsPacket::decode,
            SyncActiveSubQuestsPacket::handle);

        CHANNEL.registerMessage(nextId++,
            SyncSubQuestCountsPacket.class,
            SyncSubQuestCountsPacket::encode,
            SyncSubQuestCountsPacket::decode,
            SyncSubQuestCountsPacket::handle);

        CHANNEL.registerMessage(nextId++,
            OpenCastleLoadingScreenPacket.class,
            OpenCastleLoadingScreenPacket::encode,
            OpenCastleLoadingScreenPacket::decode,
            OpenCastleLoadingScreenPacket::handle);

        CHANNEL.registerMessage(nextId++,
            CloseCastleLoadingScreenPacket.class,
            CloseCastleLoadingScreenPacket::encode,
            CloseCastleLoadingScreenPacket::decode,
            CloseCastleLoadingScreenPacket::handle);

        CHANNEL.registerMessage(nextId++,
            SyncQuestUiVisibilityPacket.class,
            SyncQuestUiVisibilityPacket::encode,
            SyncQuestUiVisibilityPacket::decode,
            SyncQuestUiVisibilityPacket::handle);
    }
}
