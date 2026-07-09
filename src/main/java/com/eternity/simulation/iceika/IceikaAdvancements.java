package com.eternity.simulation.iceika;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class IceikaAdvancements {
    private IceikaAdvancements() {}

    static void award(ServerPlayer player, String path) {
        Advancement advancement = player.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation("simulation", "iceika/" + path));
        if (advancement == null) return;

        for (String criterion : advancement.getCriteria().keySet()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
