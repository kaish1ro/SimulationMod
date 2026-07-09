package com.eternity.simulation.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Позиция HUD текущего задания ({@link QuestHudOverlay}) — сторона экрана
 * (левая/правая) и высота (0=верх, 1=низ), настраивается из {@link QuestScreen}.
 * Хранится в отдельном properties-файле, тот же паттерн, что и масштаб
 * главного меню в {@code CustomMenuScreen}.
 */
public final class QuestHudSettings {

    public enum Side { LEFT, RIGHT }

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("simulation-quest-hud.properties");

    private static Side side = Side.RIGHT;
    private static double heightFraction = 0.5;
    private static double scale = 1.0;
    private static boolean loaded = false;

    private QuestHudSettings() {}

    public static Side getSide() {
        load();
        return side;
    }

    public static double getHeightFraction() {
        load();
        return heightFraction;
    }

    public static double getScale() {
        load();
        return scale;
    }

    public static void setSide(Side s) {
        load();
        side = s;
        save();
    }

    public static void setHeightFraction(double h) {
        load();
        heightFraction = Math.max(0.0, Math.min(1.0, h));
        save();
    }

    public static void setScale(double s) {
        load();
        scale = Math.max(0.5, Math.min(2.0, s));
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (var in = Files.newInputStream(FILE)) {
            Properties p = new Properties();
            p.load(in);
            String s = p.getProperty("side");
            if ("left".equals(s)) side = Side.LEFT;
            else if ("right".equals(s)) side = Side.RIGHT;
            String h = p.getProperty("height");
            if (h != null) heightFraction = Math.max(0.0, Math.min(1.0, Double.parseDouble(h.trim())));
            String sc = p.getProperty("scale");
            if (sc != null) scale = Math.max(0.5, Math.min(2.0, Double.parseDouble(sc.trim())));
        } catch (IOException | NumberFormatException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("side", side == Side.LEFT ? "left" : "right");
            p.setProperty("height", String.valueOf(heightFraction));
            p.setProperty("scale", String.valueOf(scale));
            try (var out = Files.newOutputStream(FILE)) {
                p.store(out, "Simulation quest HUD position");
            }
        } catch (IOException ignored) {}
    }
}
