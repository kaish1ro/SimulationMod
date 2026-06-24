package com.eternity.simulation.client;

import com.cinemamod.mcef.MCEF;
import com.eternity.simulation.SimulationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SimulationMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MenuReplacer {

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof TitleScreen && MCEF.isInitialized()) {
            event.setNewScreen(new CustomMenuScreen());
        }
    }

    // Универсальная замена фона + очистка кадра. Render.Pre постится Forge'ом
    // КАЖДЫЙ кадр ДО screen.render(), для любого экрана — в т.ч. тех, что вообще
    // не рисуют фон (EditGameRulesScreen) и потому полагаются на очищенный кадр.
    // В связке с MCEF главный таргет между кадрами не очищается, поэтому без этой
    // принудительной очистки старое содержимое (тултипы, прошлые кадры) копится.
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (Minecraft.getInstance().level != null) return;
        Screen screen = event.getScreen();
        if (screen instanceof CustomMenuScreen) return;     // у него собственный браузер-фон
        MenuBackground.clearAndDraw(event.getGuiGraphics(), screen.width, screen.height);
    }

    // Гасим тёмную землю список-виджетов (правила, эксперименты, датапаки, модовые
    // меню) — сеттеры публичные. Тогда наш фон проступает и в области списка.
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (Minecraft.getInstance().level != null) return;
        if (event.getScreen() instanceof CustomMenuScreen) return;
        disableListDirt(event.getScreen().children());
    }

    private static void disableListDirt(List<? extends GuiEventListener> kids) {
        for (GuiEventListener k : kids) {
            if (k instanceof AbstractSelectionList<?> list) {
                list.setRenderBackground(false);
                list.setRenderTopAndBottom(false);          // верх/низ-дёрн списка тоже
            } else if (k instanceof ContainerEventHandler ceh) {
                disableListDirt(ceh.children());            // на случай вложенных контейнеров
            }
        }
    }
}
