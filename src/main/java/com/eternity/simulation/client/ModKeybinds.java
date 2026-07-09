package com.eternity.simulation.client;

import com.eternity.simulation.SimulationMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ModKeybinds {

    private ModKeybinds() {}

    public static final KeyMapping OPEN_QUESTS = new KeyMapping(
            "key.simulation.open_quests", GLFW.GLFW_KEY_J, "key.categories.simulation");

    @Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_QUESTS);
        }
    }

    @Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class TickHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_QUESTS.consumeClick()) {
                if (mc.screen == null && ClientQuestState.isUiVisible()) mc.setScreen(new QuestScreen());
            }

            // Некоторые моды при потере фокуса окна (Alt+Tab) или паузе напрямую
            // подменяют экран на PauseScreen, минуя нашу защиту от закрытия в самом
            // CastleLoadingScreen — без этого экран загрузки замка просто пропадал бы.
            if (CastleLoadingScreen.isSequenceActive() && !(mc.screen instanceof CastleLoadingScreen)) {
                mc.setScreen(new CastleLoadingScreen());
            }

            // MCEF при смене курсора в HTML (hover по кнопке и т.п.) сам дёргает
            // GLFW.glfwSetInputMode(..., GLFW_CURSOR_NORMAL)/glfwSetCursor напрямую —
            // в обход MouseHandler, поэтому его приватный флаг mouseGrabbed остаётся
            // (ошибочно) не в ладах с реальным состоянием GLFW. Работает в обе
            // стороны: вход в игру мог не спрятать курсор (mouseGrabbed уже true,
            // grabMouse() выходит по своей проверке "уже схвачено") — а открытие
            // экрана (главное меню, окно заданий CustomMenuScreen/QuestScreen)
            // может НЕ показать курсор обратно, если mouseGrabbed уже false —
            // releaseMouse() тогда тоже ничего не делает, хотя реально GLFW ещё
            // держит CURSOR_DISABLED от предыдущего форса ниже. Поэтому оба
            // направления форсим напрямую по фактическому состоянию GLFW, не
            // полагаясь на этот флаг вообще.
            long handle = mc.getWindow().getWindow();
            if (mc.screen == null && mc.level != null) {
                if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
                    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    GLFW.glfwSetCursor(handle, org.lwjgl.system.MemoryUtil.NULL);
                }
            } else if (mc.screen != null) {
                if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL) {
                    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    GLFW.glfwSetCursor(handle, org.lwjgl.system.MemoryUtil.NULL);
                }
            }
        }
    }
}
