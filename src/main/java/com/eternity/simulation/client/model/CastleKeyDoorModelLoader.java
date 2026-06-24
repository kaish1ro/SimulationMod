package com.eternity.simulation.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraftforge.client.model.geometry.IGeometryLoader;

/**
 * Загрузчик кастомной модели двери замка.
 *
 * <p>Регистрируется под именем {@code "simulation:castle_key_door"} через
 * {@link net.minecraftforge.client.event.ModelEvent.RegisterGeometryLoaders}.
 *
 * <p>Используется в JSON-модели блока:
 * <pre>
 * {
 *   "loader": "simulation:castle_key_door",
 *   "textures": { "base": "...", "overlay": "...", "overlay_connected": "...", "particle": "..." }
 * }
 * </pre>
 */
public class CastleKeyDoorModelLoader implements IGeometryLoader<CastleKeyDoorGeometry> {

    public static final CastleKeyDoorModelLoader INSTANCE = new CastleKeyDoorModelLoader();

    private CastleKeyDoorModelLoader() {}

    @Override
    public CastleKeyDoorGeometry read(JsonObject jsonObject,
                                      JsonDeserializationContext context) throws JsonParseException {
        return new CastleKeyDoorGeometry();
    }
}
