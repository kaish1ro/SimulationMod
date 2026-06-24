package com.eternity.simulation.castle;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Распарсенный DATA-маркер (structure block, mode=DATA) из castle.nbt/labyrinth.nbt.
 * Формат метаданных: {@code key1=value1;key2:value2;...} (поддерживаются оба разделителя
 * ключ/значение — {@code =} и {@code :}).
 */
public final class CastleDataMarker {

    private final BlockPos pos;
    private final String rawMetadata;
    private final Map<String, String> attributes;

    public CastleDataMarker(BlockPos pos, String rawMetadata) {
        this.pos = pos;
        this.rawMetadata = rawMetadata;
        this.attributes = parse(rawMetadata);
    }

    public BlockPos pos() {
        return pos;
    }

    public String rawMetadata() {
        return rawMetadata;
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    public boolean has(String key) {
        return attributes.containsKey(key);
    }

    public String get(String key) {
        return attributes.get(key);
    }

    /** @return ключи метаданных маркера (для поиска по шаблону, напр. "Nspawnpoint"). */
    public java.util.Set<String> keys() {
        return attributes.keySet();
    }

    public int getInt(String key, int defaultValue) {
        String v = attributes.get(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Map<String, String> parse(String metadata) {
        Map<String, String> map = new LinkedHashMap<>();
        if (metadata == null || metadata.isBlank()) return map;

        for (String part : metadata.split(";")) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            int sep = indexOfSeparator(token);
            if (sep < 0) {
                map.put(token, "");
                continue;
            }

            String key = token.substring(0, sep).trim();
            String value = token.substring(sep + 1).trim();
            map.put(key, value);
        }
        return map;
    }

    private static int indexOfSeparator(String token) {
        int eq = token.indexOf('=');
        int colon = token.indexOf(':');
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }
}
