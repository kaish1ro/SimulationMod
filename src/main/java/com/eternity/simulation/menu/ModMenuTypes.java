package com.eternity.simulation.menu;

import com.eternity.simulation.SimulationMod;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, SimulationMod.MODID);

    // Клиентская фабрика получает BlockPos из пакета — передаём ContainerLevelAccess.NULL,
    // т.к. на клиенте stillValid не используется.
    public static final RegistryObject<MenuType<SimulationCraftingMenu>> SIMULATION_WORKBENCH =
        MENU_TYPES.register("simulation_workbench",
            () -> IForgeMenuType.create((windowId, inv, data) ->
                new SimulationCraftingMenu(windowId, inv,
                    net.minecraft.world.inventory.ContainerLevelAccess.NULL)));
}
