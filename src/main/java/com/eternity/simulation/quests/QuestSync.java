package com.eternity.simulation.quests;

import com.eternity.simulation.network.NetworkHandler;
import com.eternity.simulation.network.SyncActiveSubQuestsPacket;
import com.eternity.simulation.network.SyncQuestStatePacket;
import com.eternity.simulation.network.SyncSubQuestCountsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Map;

/** Рассылка полного снимка состояния квестов/подзаданий всем игрокам на сервере. */
public final class QuestSync {

    private QuestSync() {}

    public static void syncMainQuests(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncMainQuestsTo(player);
        }
    }

    public static void syncSubQuests(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncSubQuestsTo(player);
        }
    }

    public static void syncMainQuestsTo(ServerPlayer player) {
        SimulationQuestState state = SimulationQuestState.get(player.getServer().overworld());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncQuestStatePacket(new ArrayList<>(state.getCompleted())));
    }

    public static void syncSubQuestsTo(ServerPlayer player) {
        SubQuestState state = SubQuestState.get(player.getServer().overworld());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncActiveSubQuestsPacket(new ArrayList<>(state.getActive())));
    }

    public static void syncSubQuestCounts(MinecraftServer server, Map<String, Integer> counts) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new SyncSubQuestCountsPacket(counts));
        }
    }
}
