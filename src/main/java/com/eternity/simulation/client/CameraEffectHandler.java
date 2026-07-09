package com.eternity.simulation.client;

import com.eternity.simulation.SimulationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские визуальные эффекты: тряска камеры и кинематографический взгляд на точку.
 * Все поля volatile — читаются рендер-потоком, пишутся main-потоком.
 */
@Mod.EventBusSubscriber(modid = SimulationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CameraEffectHandler {

    // CEF-браузер главного меню (CustomMenuScreen) — синглтон, который раньше НИКОГДА
    // не закрывался: висел с собственным Chromium-подпроцессом и GPU-буферами до самого
    // выхода из игры, даже когда игрок давно в мире. Освобождаем его в момент фактического
    // входа в мир — второй (наш экран заданий) браузер к этому моменту уже не конкурирует
    // с ним за память.
    @SubscribeEvent
    public static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CustomMenuScreen.disposeBrowser();
    }

    private static volatile long   shakeEndMs    = 0;
    private static volatile float  shakeIntensity = 0f;

    private static volatile long   lookStartMs = 0;
    private static volatile long   lookEndMs   = 0;
    private static volatile int    lookDurationMs = 0;
    private static volatile double lookTargetX = 0, lookTargetY = 0, lookTargetZ = 0;

    /** Длительность плавного въезда/выезда камеры (мс). */
    private static final float BLEND_MS = 800f;

    /** Запустить тряску. Вызывается из сетевого пакета на main-клиент-потоке. */
    public static void startShake(float intensity, int durationMs) {
        shakeIntensity = intensity;
        shakeEndMs = System.currentTimeMillis() + durationMs;
    }

    /** Направить камеру на мировую точку на указанное время (с плавным въездом/выездом). */
    public static void startLook(double x, double y, double z, int durationMs) {
        lookTargetX = x;
        lookTargetY = y;
        lookTargetZ = z;
        lookStartMs = System.currentTimeMillis();
        lookDurationMs = durationMs;
        lookEndMs = lookStartMs + durationMs;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.currentTimeMillis();

        // ── Кинематографический взгляд с плавным въездом/выездом ──────────────────
        if (now < lookEndMs) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 eye = mc.player.getEyePosition((float) event.getPartialTick());
                double dx = lookTargetX + 0.5 - eye.x;
                double dy = lookTargetY + 0.5 - eye.y;
                double dz = lookTargetZ + 0.5 - eye.z;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                float targetYaw   = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
                float targetPitch = (float) (-Math.atan2(dy, horizDist) * 180.0 / Math.PI);

                // factor 0 → собственный взгляд игрока, 1 → полностью на цели
                float factor = lookBlendFactor(now);

                event.setYaw(lerpAngle(event.getYaw(), targetYaw, factor));
                event.setPitch(lerp(event.getPitch(), targetPitch, factor));
            }
        }

        // ── Тряска камеры ────────────────────────────────────────────────────────
        if (now < shakeEndMs) {
            long remaining = shakeEndMs - now;
            // Интенсивность плавно спадает к концу, амплитуда колеблется по синусу
            float envelope = Math.min(1f, remaining / 300f); // первые 300мс — нарастание, потом убывание
            float shake = shakeIntensity * envelope * (float) Math.sin(now * 0.04);
            event.setYaw(event.getYaw() + shake);
            event.setPitch(event.getPitch() + shake * 0.35f);
        }
    }

    /** Коэффициент смешивания: плавный въезд за BLEND_MS, удержание, плавный выезд за BLEND_MS. */
    private static float lookBlendFactor(long now) {
        long elapsed   = now - lookStartMs;
        long remaining = lookEndMs - now;
        float t;
        if (elapsed < BLEND_MS) {
            t = elapsed / BLEND_MS;            // въезд
        } else if (remaining < BLEND_MS) {
            t = remaining / BLEND_MS;          // выезд
        } else {
            t = 1f;                            // удержание
        }
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);          // smoothstep
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    /** Линейная интерполяция углов по кратчайшей дуге (учёт перехода через ±180°). */
    private static float lerpAngle(float a, float b, float f) {
        float delta = b - a;
        while (delta > 180f)  delta -= 360f;
        while (delta < -180f) delta += 360f;
        return a + delta * f;
    }
}
