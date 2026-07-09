package com.eternity.simulation.mixin;

import net.minecraftforge.fml.loading.FMLPaths;
import org.cef.CefSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * MCEF (com.cinemamod.mcef.CefUtil#init) никогда не задаёт {@code CefSettings.log_file}
 * (проверено декомпиляцией — поле остаётся {@code null}). Нативный CEF в этом случае
 * сам подставляет "debug.log" рядом со своими нативными библиотеками — а те
 * извлекаются в ОБЩИЙ для всех установок каталог общего Java-рантайма
 * (.../java-runtime-gamma/windows-x64/java-runtime-gamma/bin/). При параллельном
 * запуске двух экземпляров игры оба JCEF-процесса дерутся за один и тот же
 * файл лога — второй не может ни открыть, ни удалить его, пока первый держит хендл.
 *
 * <p>cache_path сам мод уже кладёт per-instance (gameDir/mods/mcef-cache) — делаем
 * то же самое для log_file, перехватывая аргумент прямо перед CefApp.getInstance(...).
 */
@Mixin(targets = "com.cinemamod.mcef.CefUtil", remap = false)
public abstract class CefUtilLogPathMixin {

    @ModifyArg(method = "init",
            at = @At(value = "INVOKE",
                    target = "Lorg/cef/CefApp;getInstance([Ljava/lang/String;Lorg/cef/CefSettings;)Lorg/cef/CefApp;"),
            index = 1)
    private static CefSettings simulation$perInstanceLogFile(CefSettings settings) {
        settings.log_file = FMLPaths.GAMEDIR.get()
                .resolve("mods").resolve("mcef-cache").resolve("debug.log")
                .toAbsolutePath().toString();
        return settings;
    }
}
