package com.eternity.simulation.castle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Восстанавливает реестр кастомных картин Immersive Paintings для картин,
 * вшитых в castle.nbt.
 *
 * Сущности картин ({@code immersive_paintings:glow_painting}) хранят в NBT только
 * ссылку {@code Motive} (ResourceLocation) на запись в реестре {@code Painting}
 * (имя/автор/размеры/hash изображения). Сам реестр — это {@code SavedData},
 * которая пишется в {@code data/immersive_paintings.dat} КОНКРЕТНОГО мира, а не
 * в NBT структуры. Поэтому после переноса castle.nbt в новый мир записи в реестре
 * отсутствуют, и картины рендерятся пустыми, даже если PNG из
 * {@code immersive_paintings_cache/} (по hash) скопированы вручную.
 *
 * Здесь восстанавливаем недостающие записи реестра по данным, снятым с рабочего
 * мира. Картинка по {@code hash} ищется модом в {@code immersive_paintings_cache/}
 * — её перенос пользователь уже делает отдельно.
 *
 * Работает через рефлексию, т.к. immersive_paintings не является compile-time
 * зависимостью мода.
 */
public final class CastlePaintingRestoreTask {

    private CastlePaintingRestoreTask() {}

    private record Entry(String motive, String name, int width, int height, int resolution, String hash) {}

    private static final String AUTHOR = "Gunter_Lord";

    private static final Entry[] ENTRIES = {
        new Entry("immersive_paintings:gunter_lord/429fb83ef4df7ce4fd047f42eb5b7e9e", "429fb83ef4df7ce4fd047f42eb5b7e9e", 7, 7, 32, "918353ec-6b2f-439d-8dcf-436ac17cf453"),
        new Entry("immersive_paintings:gunter_lord/72d5b2dc05f2cd6429de5844008ffb0f", "72d5b2dc05f2cd6429de5844008ffb0f", 4, 4, 32, "ed53a666-3ca6-431c-89ee-39a2a83c7468"),
        new Entry("immersive_paintings:gunter_lord/33b9e022686336a8c1d468c7bcbbb0d7", "33b9e022686336a8c1d468c7bcbbb0d7", 7, 7, 32, "71d9e640-b69b-456d-8e08-9fae144c8e2a"),
        new Entry("immersive_paintings:gunter_lord/1dc79bf42a38813b3ccfe691839d55d7", "1dc79bf42a38813b3ccfe691839d55d7", 4, 4, 32, "01ad87e4-a843-4605-ad1c-c2a576d86860"),
        new Entry("immersive_paintings:gunter_lord/90fee38ebf70b623b13788909832154d", "90fee38ebf70b623b13788909832154d", 4, 4, 16, "e75839db-2fb4-49fa-8afa-33d6c4af8c8d"),
        new Entry("immersive_paintings:gunter_lord/51604ee80d6fc5ec1cf91d79b0d6b90b", "51604ee80d6fc5ec1cf91d79b0d6b90b", 2, 2, 32, "472dcc75-86f9-4248-b7ab-9511cea4537a"),
        new Entry("immersive_paintings:gunter_lord/21cc34ca0f842ca749ff87ae70ab7733", "21cc34ca0f842ca749ff87ae70ab7733", 5, 5, 32, "4294e03e-fff4-4f14-811a-d96da0e13f82"),
        new Entry("immersive_paintings:gunter_lord/93de20250642ddff4038d2519cc414b6", "93de20250642ddff4038d2519cc414b6", 2, 2, 32, "cafd2d0e-7448-441c-b5b5-857218eb34c9"),
        new Entry("immersive_paintings:gunter_lord/03aa75263030d9904010970914cf9eca", "03aa75263030d9904010970914cf9eca", 5, 5, 32, "4d0dd92d-4025-46ff-8f05-4d7762b5f12f"),
        new Entry("immersive_paintings:gunter_lord/8bde7365d1872a3231ab3e4d58b98f11", "8bde7365d1872a3231ab3e4d58b98f11", 5, 5, 32, "a223bd8e-6a22-4e1d-a7bf-9ab7ff3084cc"),
        new Entry("immersive_paintings:gunter_lord/acea2f56b067a0eb2548a7c4b4124a1b", "acea2f56b067a0eb2548a7c4b4124a1b", 4, 4, 32, "d4855096-b34a-4308-b006-2f737521b3a3"),
        new Entry("immersive_paintings:gunter_lord/c1b05228b1793079e28c1f912b821f3f", "c1b05228b1793079e28c1f912b821f3f", 7, 7, 16, "64244d19-ca27-4c29-aef3-e1d57d58ab7b"),
        new Entry("immersive_paintings:gunter_lord/947d1521f5f6e82c622efc09f7f7b37b", "947d1521f5f6e82c622efc09f7f7b37b", 3, 3, 32, "881cfe6c-eee2-497d-8fc1-3f2495abcfba"),
        new Entry("immersive_paintings:gunter_lord/d2390baf21d7123e792728d588a56c7a", "d2390baf21d7123e792728d588a56c7a", 15, 11, 16, "0861b9b2-a795-4fb8-97be-4fb6e3098a73"),
    };

    /**
     * @return количество вновь зарегистрированных записей, либо -1, если
     *         Immersive Paintings не установлен.
     */
    public static int restore(ServerLevel level) {
        try {
            Class<?> managerClass = Class.forName("immersive_paintings.resources.ServerPaintingManager");
            Class<?> paintingClass = Class.forName("immersive_paintings.resources.Painting");
            Class<?> byteImageClass = Class.forName("immersive_paintings.resources.ByteImage");
            Class<?> customPaintingsClass = Class.forName("immersive_paintings.resources.ServerPaintingManager$CustomServerPaintings");

            Method getMethod = managerClass.getMethod("get");
            Method getCustomMapMethod = customPaintingsClass.getMethod("getCustomServerPaintings");
            Method registerMethod = managerClass.getMethod("registerPainting", ResourceLocation.class, paintingClass);

            Constructor<?> ctor = paintingClass.getConstructor(
                byteImageClass, int.class, int.class, int.class,
                String.class, String.class, boolean.class, boolean.class, boolean.class, String.class);

            Object customServerPaintings = getMethod.invoke(null);
            @SuppressWarnings("unchecked")
            Map<Object, Object> registry = (Map<Object, Object>) getCustomMapMethod.invoke(customServerPaintings);

            int registered = 0;
            for (Entry entry : ENTRIES) {
                ResourceLocation motive = new ResourceLocation(entry.motive());
                if (registry.containsKey(motive)) continue;

                Object painting = ctor.newInstance(
                    null, entry.width(), entry.height(), entry.resolution(),
                    entry.name(), AUTHOR, false, false, false, entry.hash());

                registerMethod.invoke(null, motive, painting);
                registered++;
            }
            return registered;
        } catch (ClassNotFoundException e) {
            return -1;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
