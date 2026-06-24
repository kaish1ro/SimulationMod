package com.eternity.simulation.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import org.joml.Matrix4f;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Function;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class CustomMenuScreen extends Screen {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("simulation.Menu");

    // Синглтон-браузер — переживает смену экранов (SelectWorldScreen → Back → это меню)
    private static MCEFBrowser browser;
    private static CefMessageRouter messageRouter;
    private static boolean routerAdded = false;

    // Пользовательский масштаб меню (NaN = ещё не задан, берётся авто от разрешения)
    private static double userScale = Double.NaN;
    private static boolean scaleLoaded = false;
    private static final Path SCALE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("simulation-menu.properties");

    // Папка с музыкой — <gamedir>/simulation-music/
    private static final Path MUSIC_DIR =
            FMLPaths.GAMEDIR.get().resolve("simulation-music");
    private static Thread watcherThread = null;

    private static final DateTimeFormatter SAVE_DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("ru"));

    // Игровые правила нового мира — редактируются ванильным EditGameRulesScreen,
    // применяются при создании. Сбрасываются при входе на экран создания.
    private static GameRules pendingGameRules = new GameRules();

    // Датапаки + эксперименты нового мира. PackRepository.getRequestedFeatureFlags()
    // даёт итоговые feature-флаги без перезагрузки реестров — конфиг применяется
    // в LevelSettings при создании, а createFreshLevel сам подгрузит паки.
    private static WorldDataConfiguration pendingDataConfig = WorldDataConfiguration.DEFAULT;
    private static Path packTempDir = null;

    public CustomMenuScreen() {
        super(Component.empty());
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();

        // ВАЖНО: router должен быть зарегистрирован ДО создания браузера, иначе
        // window.cefQuery не инжектится в V8-контекст страницы и весь JS↔Java мост
        // молча мёртв (cefAction проверяет typeof cefQuery !== 'undefined').
        if (messageRouter == null) {
            CefMessageRouter.CefMessageRouterConfig cfg =
                    new CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel");
            messageRouter = CefMessageRouter.create(cfg, new CefMessageRouterHandlerAdapter() {
                @Override
                public boolean onQuery(CefBrowser b, CefFrame f, long id,
                                      String request, boolean persistent, CefQueryCallback cb) {
                    LOGGER.info("[menu] cefQuery: {}", request);
                    handleAction(request, cb);
                    return true;
                }
            });
        }
        if (!routerAdded) {
            MCEF.getClient().getHandle().addMessageRouter(messageRouter);
            routerAdded = true;
        }

        if (browser == null) {
            // MCEF резолвит mod://<ns>/<path> в assets/<ns>/html/<path> — сегмент html обязателен
            browser = MCEF.createBrowser("mod://simulation/menu.html", true, physW, physH);
        } else {
            browser.resize(physW, physH);
        }

        MusicServer.start(MUSIC_DIR);
        startMusicWatcher();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        if (browser != null) {
            browser.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        }
    }

    // Масштаб применяется на стороне HTML через CSS zoom (setZoomLevel у CEF для
    // OSR-браузера ненадёжен). Java лишь хранит и отдаёт значение.
    // Если пользователь задал масштаб — используем его, иначе авто от разрешения
    // (база 1080p), чтобы на 1440p/4K меню не было мелким.
    private double effectiveScale() {
        loadScale();
        double scale = Double.isNaN(userScale)
                ? minecraft.getWindow().getHeight() / 1080.0
                : userScale;
        return Math.max(0.8, Math.min(3.0, scale));
    }

    private static void loadScale() {
        if (scaleLoaded) return;
        scaleLoaded = true;
        if (!Files.exists(SCALE_FILE)) return;
        try (var in = Files.newInputStream(SCALE_FILE)) {
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("menu_scale");
            if (v != null) userScale = Double.parseDouble(v.trim());
        } catch (IOException | NumberFormatException ignored) {}
    }

    private static void saveScale(double scale) {
        userScale = scale;
        try {
            Files.createDirectories(SCALE_FILE.getParent());
            Properties p = new Properties();
            p.setProperty("menu_scale", String.valueOf(scale));
            try (var out = Files.newOutputStream(SCALE_FILE)) {
                p.store(out, "Simulation custom menu settings");
            }
        } catch (IOException ignored) {}
    }

    // router НЕ снимаем при уходе с экрана — он привязан к живущему синглтон-браузеру
    // и должен оставаться зарегистрированным, чтобы cefQuery работал после возврата.
    // Полная очистка — только в disposeBrowser().

    public static void disposeBrowser() {
        if (messageRouter != null) {
            try { MCEF.getClient().getHandle().removeMessageRouter(messageRouter); } catch (Exception ignored) {}
            messageRouter.dispose();
            messageRouter = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    // ─── Music folder ─────────────────────────────────────────────────────────

    private static void ensureMusicDir() {
        try { Files.createDirectories(MUSIC_DIR); } catch (IOException ignored) {}
    }

    // java.awt.Desktop под Minecraft часто не работает (конфликт AWT с GLFW),
    // поэтому на Windows открываем через explorer.exe, иначе — фолбэк на Desktop.
    private static void openMusicFolder() {
        ensureMusicDir();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", MUSIC_DIR.toString()).start();
            } else if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(MUSIC_DIR.toFile());
            }
        } catch (Exception e) {
            LOGGER.warn("[menu] openmusicfolder failed: {}", e.getMessage());
        }
    }

    private static String buildTracksJson() {
        ensureMusicDir();
        try {
            List<Path> files = Files.list(MUSIC_DIR)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".mp3") || n.endsWith(".ogg")
                                || n.endsWith(".flac") || n.endsWith(".wav");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < files.size(); i++) {
                Path f = files.get(i);
                String name = f.getFileName().toString();
                String title = name.replaceAll("\\.[^.]+$", "").toUpperCase();
                // отдаём через localhost-сервер, а не file:// (Chromium его блокирует)
                String uri = MusicServer.urlFor(name);
                if (i > 0) sb.append(",");
                sb.append("{\"title\":\"").append(jsonEscape(title))
                  .append("\",\"src\":\"").append(jsonEscape(uri)).append("\"}");
            }
            return sb.append("]").toString();
        } catch (IOException e) {
            return "[]";
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void startMusicWatcher() {
        if (watcherThread != null && watcherThread.isAlive()) return;
        ensureMusicDir();
        watcherThread = new Thread(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                MUSIC_DIR.register(ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = ws.poll(1, TimeUnit.SECONDS);
                    if (key == null) continue;
                    key.pollEvents();
                    key.reset();
                    if (browser == null) continue;
                    String json = buildTracksJson();
                    // экранируем для вставки в JS-строку
                    String escaped = json.replace("\\", "\\\\").replace("'", "\\'");
                    browser.executeJavaScript(
                            "if(typeof refreshTracks==='function')refreshTracks('" + escaped + "')",
                            "", 0);
                }
            } catch (InterruptedException | IOException ignored) {}
        }, "sim-music-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    // ─── Saves ────────────────────────────────────────────────────────────────

    private static String buildSavesJson() {
        Path savesDir = FMLPaths.GAMEDIR.get().resolve("saves");
        try {
            if (!Files.isDirectory(savesDir)) return "[]";
            // level.dat читаем один раз на мир (а не дважды — для сортировки и для данных),
            // это и было основной причиной заметной задержки появления списка миров.
            List<Map.Entry<Path, CompoundTag>> worlds = Files.list(savesDir)
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("level.dat")))
                    .map(p -> Map.entry(p, readLevelData(p)))
                    .sorted(Comparator.comparingLong((Map.Entry<Path, CompoundTag> e) -> e.getValue().getLong("LastPlayed")).reversed())
                    .collect(Collectors.toList());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < worlds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(worldToJson(worlds.get(i).getKey(), worlds.get(i).getValue()));
            }
            return sb.append("]").toString();
        } catch (IOException e) {
            return "[]";
        }
    }

    private static CompoundTag readLevelData(Path dir) {
        try {
            CompoundTag root = NbtIo.readCompressed(dir.resolve("level.dat").toFile());
            return root.getCompound("Data");
        } catch (Exception e) { return new CompoundTag(); }
    }

    private static String worldToJson(Path dir, CompoundTag data) {
        String id   = dir.getFileName().toString();
        String name = id;
        long lastPlayed = 0;
        int  gameType   = 0;
        try {
            String ln = data.getString("LevelName");
            if (!ln.isEmpty()) name = ln;
            lastPlayed = data.getLong("LastPlayed");
            gameType   = data.getInt("GameType");
        } catch (Exception ignored) {}

        String dateStr = lastPlayed > 0
                ? Instant.ofEpochMilli(lastPlayed).atZone(ZoneId.systemDefault()).format(SAVE_DATE_FMT)
                : "Неизвестно";
        String[] modes = {"Выживание", "Творческий", "Приключение", "Наблюдатель"};
        String mode = (gameType >= 0 && gameType < modes.length) ? modes[gameType] : "Выживание";

        String icon = "";
        Path iconFile = dir.resolve("icon.png");
        if (Files.exists(iconFile)) {
            try {
                byte[] bytes = Files.readAllBytes(iconFile);
                icon = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            } catch (IOException ignored) {}
        }

        return "{\"id\":\""         + jsonEscape(id)
             + "\",\"name\":\""     + jsonEscape(name)
             + "\",\"lastPlayed\":\"" + jsonEscape(dateStr)
             + "\",\"gameMode\":\"" + jsonEscape(mode)
             + "\",\"icon\":\""     + icon + "\"}";
    }

    // ─── World creation ───────────────────────────────────────────────────────

    // createFreshLevel сам грузит реестры/worldgen асинхронно (managedBlock внутри),
    // поэтому отдельный WorldCreationContext не нужен. Дефолтный тип мира (NORMAL),
    // строения вкл, бонус-сундук выкл. Игровые правила берутся из pendingGameRules.
    private static void createWorld(Minecraft mc, String name, String gm, String dif,
                                    boolean cheats, String seedStr, String worldType,
                                    boolean structures, boolean bonus) {
        GameType type;
        boolean hardcore = false;
        switch (gm) {
            case "creative"  -> type = GameType.CREATIVE;
            case "adventure" -> type = GameType.ADVENTURE;
            case "hardcore"  -> { type = GameType.SURVIVAL; hardcore = true; }
            default          -> type = GameType.SURVIVAL;
        }
        Difficulty difficulty = switch (dif) {
            case "peaceful" -> Difficulty.PEACEFUL;
            case "easy"     -> Difficulty.EASY;
            case "hard"     -> Difficulty.HARD;
            default         -> Difficulty.NORMAL;
        };
        if (hardcore) difficulty = Difficulty.HARD;          // хардкор форсит сложность
        boolean allowCommands = cheats && !hardcore;

        LevelSettings settings = new LevelSettings(
                name, type, hardcore, difficulty, allowCommands,
                pendingGameRules, pendingDataConfig);

        OptionalLong parsed = WorldOptions.parseSeed(seedStr == null ? "" : seedStr.trim());
        long seed = parsed.orElseGet(WorldOptions::randomSeed);
        WorldOptions options = new WorldOptions(seed, structures, bonus);

        // Тип мира → ключ пресета (полный id, напр. minecraft:flat или terralith:overworld).
        // createFreshLevel сам подставит RegistryAccess и возьмёт WorldDimensions пресета.
        ResourceKey<WorldPreset> presetKey =
                (worldType == null || worldType.isBlank() || worldType.equals("default"))
                        ? WorldPresets.NORMAL
                        : ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation(worldType));
        Function<RegistryAccess, WorldDimensions> dims = ra ->
                ra.registryOrThrow(Registries.WORLD_PRESET)
                  .getHolderOrThrow(presetKey).value().createWorldDimensions();

        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, dims);
    }

    // Репозиторий паков для создания мира (ваниль + моды). temp-папка нужна
    // PackSelectionScreen, чтобы пользователь мог докидывать туда свои датапаки.
    private static PackRepository buildWorldPackRepo() throws IOException {
        if (packTempDir == null) packTempDir = Files.createTempDirectory("sim-datapacks-");
        PackRepository repo = ServerPacksSource.createPackRepository(packTempDir);
        repo.reload();
        repo.setSelected(pendingDataConfig.dataPacks().getEnabled());
        return repo;
    }

    // Собирает WorldDataConfiguration из выбора репозитория. getRequestedFeatureFlags()
    // даёт итоговые feature-флаги выбранных паков — перезагрузка реестров не нужна.
    private static WorldDataConfiguration deriveDataConfig(PackRepository repo) {
        List<String> selected = new java.util.ArrayList<>(repo.getSelectedIds());
        List<String> disabled = repo.getAvailableIds().stream()
                .filter(id -> !selected.contains(id))
                .collect(Collectors.toList());
        return new WorldDataConfiguration(
                new DataPackConfig(selected, disabled), repo.getRequestedFeatureFlags());
    }

    // Перечисляет типы мира из тега #minecraft:normal. Каждый мод, добавляющий свой тип
    // в выбор, кладёт data/minecraft/tags/worldgen/world_preset/normal.json в свой jar —
    // ClassLoader.getResources собирает их все. Имена берём из I18n (generator.<ns>.<path>).
    private static String buildWorldTypesJson() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        try {
            var res = CustomMenuScreen.class.getClassLoader()
                    .getResources("data/minecraft/tags/worldgen/world_preset/normal.json");
            com.google.gson.Gson gson = new com.google.gson.Gson();
            while (res.hasMoreElements()) {
                java.net.URL url = res.nextElement();
                try (var in = url.openStream()) {
                    var obj = gson.fromJson(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8),
                            com.google.gson.JsonObject.class);
                    if (obj == null || !obj.has("values")) continue;
                    for (var el : obj.getAsJsonArray("values")) {
                        String id = el.isJsonObject()
                                ? el.getAsJsonObject().get("id").getAsString()
                                : el.getAsString();
                        if (id != null && !id.isEmpty()) ids.add(id);
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}

        // ванильные в каноничном порядке первыми, затем модовые в порядке обнаружения
        String[] vanilla = {"minecraft:normal", "minecraft:large_biomes",
                "minecraft:amplified", "minecraft:flat", "minecraft:single_biome_surface"};
        java.util.List<String> ordered = new java.util.ArrayList<>();
        for (String v : vanilla) if (ids.remove(v)) ordered.add(v);
        ordered.addAll(ids);
        if (ordered.isEmpty()) ordered.addAll(java.util.Arrays.asList(vanilla));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ordered.size(); i++) {
            String id = ordered.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(jsonEscape(id))
              .append("\",\"name\":\"").append(jsonEscape(worldTypeName(id))).append("\"}");
        }
        return sb.append("]").toString();
    }

    private static String worldTypeName(String id) {
        int c = id.indexOf(':');
        String ns   = c >= 0 ? id.substring(0, c) : "minecraft";
        String path = c >= 0 ? id.substring(c + 1) : id;
        String key  = "generator." + ns + "." + path;
        String tr   = I18n.get(key);
        if (!tr.equals(key)) return tr;             // перевод найден
        return switch (id) {                        // запасные имена для ванильных
            case "minecraft:normal"               -> "По умолчанию";
            case "minecraft:large_biomes"         -> "Большие биомы";
            case "minecraft:amplified"            -> "Усиленный";
            case "minecraft:flat"                 -> "Суперплоский";
            case "minecraft:single_biome_surface" -> "Один биом";
            default                               -> path;
        };
    }

    // ─── Servers ──────────────────────────────────────────────────────────────

    private static Path serversFile() { return FMLPaths.GAMEDIR.get().resolve("servers.dat"); }

    private static CompoundTag readServersNbt() throws IOException {
        Path f = serversFile();
        if (!Files.exists(f)) return new CompoundTag();
        try { return NbtIo.readCompressed(f.toFile()); }
        catch (IOException ex) { return NbtIo.read(f.toFile()); }
    }

    private static void writeServersNbt(CompoundTag root) throws IOException {
        try (var out = Files.newOutputStream(serversFile())) { NbtIo.writeCompressed(root, out); }
    }

    private static String buildServersJson() {
        try {
            CompoundTag root = readServersNbt();
            ListTag list = root.getList("servers", 10);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                CompoundTag e = list.getCompound(i);
                String name = e.getString("name");
                String ip   = e.getString("ip");
                String icon = e.getString("icon"); // уже base64 PNG
                String iconUri = icon.isEmpty() ? "" : "data:image/png;base64," + icon;
                sb.append("{\"idx\":").append(i)
                  .append(",\"name\":\"").append(jsonEscape(name)).append("\"")
                  .append(",\"ip\":\"").append(jsonEscape(ip)).append("\"")
                  .append(",\"icon\":\"").append(iconUri).append("\"}");
            }
            return sb.append("]").toString();
        } catch (Exception e) {
            LOGGER.warn("[menu] buildServersJson: {}", e.getMessage());
            return "[]";
        }
    }

    private static void addOrEditServer(int editIdx, String name, String ip) {
        try {
            CompoundTag root = readServersNbt();
            ListTag list = root.contains("servers") ? root.getList("servers", 10) : new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putString("name", name);
            entry.putString("ip", ip);
            if (editIdx >= 0 && editIdx < list.size()) {
                // сохраняем иконку если была
                CompoundTag old = list.getCompound(editIdx);
                if (old.contains("icon")) entry.putString("icon", old.getString("icon"));
                list.set(editIdx, entry);
            } else {
                list.add(entry);
            }
            root.put("servers", list);
            writeServersNbt(root);
        } catch (Exception e) {
            LOGGER.warn("[menu] addOrEditServer: {}", e.getMessage());
        }
    }

    private static void deleteServer(int idx) {
        try {
            CompoundTag root = readServersNbt();
            ListTag list = root.getList("servers", 10);
            if (idx >= 0 && idx < list.size()) list.remove(idx);
            root.put("servers", list);
            writeServersNbt(root);
        } catch (Exception e) {
            LOGGER.warn("[menu] deleteServer: {}", e.getMessage());
        }
    }

    private static void pushServers() {
        if (browser == null) return;
        String json = buildServersJson();
        String esc  = json.replace("\\", "\\\\").replace("'", "\\'");
        browser.executeJavaScript("if(typeof refreshServers==='function')refreshServers('" + esc + "')", "", 0);
    }

    // ─── JS → Java bridge ────────────────────────────────────────────────────

    private void handleAction(String action, CefQueryCallback callback) {
        // Файловые операции — не требуют game thread
        if (action.equals("gettracks")) {
            callback.success(buildTracksJson());
            return;
        }
        if (action.equals("getsaves")) {
            // Сразу отвечаем "loading" — JS будет ждать executeJavaScript push
            callback.success("loading");
            Thread t = new Thread(() -> {
                String json = buildSavesJson();
                String esc = json.replace("\\", "\\\\").replace("'", "\\'");
                if (browser != null)
                    browser.executeJavaScript(
                            "if(typeof refreshSaves==='function')refreshSaves('" + esc + "')", "", 0);
            });
            t.setDaemon(true);
            t.start();
            return;
        }
        if (action.startsWith("opensave:")) {
            String levelId = action.substring("opensave:".length());
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            // loadLevel(Screen errorScreen, String levelId) — официальное имя метода по Mojang-маппингу.
            // Ловим исключения, иначе они тонут в нативном колбэке CEF и игра виснет (см. createsave).
            mc.execute(() -> {
                try {
                    mc.createWorldOpenFlows().loadLevel(new CustomMenuScreen(), levelId);
                } catch (Throwable t) {
                    LOGGER.error("[menu] не удалось загрузить мир " + levelId, t);
                    mc.setScreen(new CustomMenuScreen());
                }
            });
            return;
        }
        if (action.startsWith("editsave:")) {
            String levelId = action.substring("editsave:".length());
            callback.success("ok");
            Thread t = new Thread(() -> {
                try {
                    var access = Minecraft.getInstance().getLevelSource().createAccess(levelId);
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new EditWorldScreen(confirmed -> {
                        try { access.close(); } catch (Exception ignored) {}
                        mc.setScreen(new CustomMenuScreen());
                    }, access)));
                } catch (Exception e) {
                    LOGGER.warn("[menu] editsave failed: {}", e.getMessage());
                }
            });
            t.setDaemon(true);
            t.start();
            return;
        }
        if (action.startsWith("deletesave:")) {
            String levelId = action.substring("deletesave:".length());
            callback.success("ok");
            Thread t = new Thread(() -> {
                try {
                    Path worldPath = FMLPaths.GAMEDIR.get().resolve("saves").resolve(levelId);
                    if (Files.isDirectory(worldPath)) {
                        Files.walk(worldPath)
                                .sorted(Comparator.reverseOrder())
                                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                    }
                    if (browser != null) {
                        String json = buildSavesJson();
                        String esc = json.replace("\\", "\\\\").replace("'", "\\'");
                        browser.executeJavaScript(
                                "if(typeof refreshSaves==='function')refreshSaves('" + esc + "')", "", 0);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[menu] deletesave failed: {}", e.getMessage());
                }
            });
            t.setDaemon(true);
            t.start();
            return;
        }
        if (action.equals("createsave")) {
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            // openFresh загружает worldgen-реестры и может бросить (например, из-за
            // сломанных datapack-структур у модов). Этот код выполняется внутри
            // нативного колбэка CEF (N_DoMessageLoopWork), который проглатывает Java-
            // исключения молча → "Подготовка к созданию мира" висит вечно. Ловим сами
            // и возвращаем в меню вместо зависания.
            mc.execute(() -> {
                try {
                    CreateWorldScreen.openFresh(mc, new CustomMenuScreen());
                } catch (Throwable t) {
                    LOGGER.error("[menu] не удалось открыть создание мира "
                            + "(вероятно, сломанные worldgen-данные мода)", t);
                    mc.setScreen(new CustomMenuScreen());
                }
            });
            return;
        }
        if (action.equals("resetcreate")) {
            // Вход на экран создания — сбрасываем накопленные правила и паки
            pendingGameRules = new GameRules();
            pendingDataConfig = WorldDataConfiguration.DEFAULT;
            callback.success("ok");
            return;
        }
        if (action.startsWith("createworld:")) {
            // payload: name||gametype||difficulty||cheats||seed||worldtype||structures||bonus
            String[] p = action.substring("createworld:".length()).split("\\|\\|", 8);
            String name = (p.length > 0 && !p[0].isBlank()) ? p[0] : "Новый мир";
            String gm   = p.length > 1 ? p[1] : "survival";
            String dif  = p.length > 2 ? p[2] : "normal";
            boolean cheats = p.length > 3 && p[3].equals("true");
            String seed = p.length > 4 ? p[4] : "";
            String wt   = p.length > 5 ? p[5] : "default";
            boolean structures = p.length < 7 || p[6].equals("true");   // по умолчанию вкл
            boolean bonus      = p.length > 7 && p[7].equals("true");
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    createWorld(mc, name, gm, dif, cheats, seed, wt, structures, bonus);
                } catch (Throwable t) {
                    LOGGER.error("[menu] не удалось создать мир", t);
                    mc.setScreen(new CustomMenuScreen());
                }
            });
            return;
        }
        if (action.equals("cwgamerules")) {
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new EditGameRulesScreen(pendingGameRules.copy(), opt -> {
                opt.ifPresent(gr -> pendingGameRules = gr);
                // Браузер-синглтон переживает смену экрана — HTML остаётся на экране создания
                mc.setScreen(new CustomMenuScreen());
            })));
            return;
        }
        if (action.equals("getworldtypes")) {
            callback.success(buildWorldTypesJson());
            return;
        }
        if (action.equals("cwdatapacks")) {
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    PackRepository repo = buildWorldPackRepo();
                    mc.setScreen(new PackSelectionScreen(repo, r -> {
                        pendingDataConfig = deriveDataConfig(r);
                        mc.setScreen(new CustomMenuScreen());
                    }, packTempDir, Component.translatable("dataPack.title")) {
                        @Override public void onClose() { mc.setScreen(new CustomMenuScreen()); }
                    });
                } catch (Exception e) {
                    LOGGER.warn("[menu] datapacks failed: {}", e.getMessage());
                    mc.setScreen(new CustomMenuScreen());
                }
            });
            return;
        }
        if (action.equals("cwexperiments")) {
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    PackRepository repo = buildWorldPackRepo();
                    // конструктор ExperimentsScreen protected — обходим анонимным подклассом
                    mc.setScreen(new ExperimentsScreen(new CustomMenuScreen(), repo, r -> {
                        pendingDataConfig = deriveDataConfig(r);
                        mc.setScreen(new CustomMenuScreen());
                    }) {});
                } catch (Exception e) {
                    LOGGER.warn("[menu] experiments failed: {}", e.getMessage());
                    mc.setScreen(new CustomMenuScreen());
                }
            });
            return;
        }
        if (action.equals("getservers")) {
            callback.success("loading");
            Thread t = new Thread(() -> pushServers());
            t.setDaemon(true); t.start();
            return;
        }
        if (action.equals("refreshservers")) {
            callback.success("loading");
            Thread t = new Thread(() -> pushServers());
            t.setDaemon(true); t.start();
            return;
        }
        if (action.startsWith("connectserver:")) {
            // формат: connectserver:<ip>||<name>
            String payload = action.substring("connectserver:".length());
            String[] parts = payload.split("\\|\\|", 2);
            String ip   = parts[0];
            String name = parts.length > 1 ? parts[1] : ip;
            callback.success("ok");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                ServerData sd = new ServerData(name, ip, false);
                ConnectScreen.startConnecting(new CustomMenuScreen(), mc, ServerAddress.parseString(ip), sd, false);
            });
            return;
        }
        if (action.startsWith("addserver:")) {
            // формат: addserver:<name>||<ip>
            String payload = action.substring("addserver:".length());
            String[] parts = payload.split("\\|\\|", 2);
            String name = parts[0];
            String ip   = parts.length > 1 ? parts[1] : "";
            callback.success("ok");
            Thread t = new Thread(() -> { addOrEditServer(-1, name, ip); pushServers(); });
            t.setDaemon(true); t.start();
            return;
        }
        if (action.startsWith("editserver:")) {
            // формат: editserver:<idx>||<name>||<ip>
            String payload = action.substring("editserver:".length());
            String[] parts = payload.split("\\|\\|", 3);
            try {
                int idx = Integer.parseInt(parts[0]);
                String name = parts.length > 1 ? parts[1] : "";
                String ip   = parts.length > 2 ? parts[2] : "";
                callback.success("ok");
                Thread t = new Thread(() -> { addOrEditServer(idx, name, ip); pushServers(); });
                t.setDaemon(true); t.start();
            } catch (NumberFormatException ignored) { callback.failure(-1, "bad idx"); }
            return;
        }
        if (action.startsWith("deleteserver:")) {
            try {
                int idx = Integer.parseInt(action.substring("deleteserver:".length()));
                callback.success("ok");
                Thread t = new Thread(() -> { deleteServer(idx); pushServers(); });
                t.setDaemon(true); t.start();
            } catch (NumberFormatException ignored) { callback.failure(-1, "bad idx"); }
            return;
        }
        if (action.equals("openmusicfolder")) {
            callback.success("ok");
            Thread t = new Thread(CustomMenuScreen::openMusicFolder);
            t.setDaemon(true);
            t.start();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // Ползунок масштаба: HTML запрашивает текущее значение и шлёт новое
            if (action.equals("getzoom")) {
                callback.success(String.valueOf(effectiveScale()));
                return;
            }
            if (action.startsWith("zoom:")) {
                try {
                    saveScale(Double.parseDouble(action.substring(5)));
                    callback.success("ok");
                } catch (NumberFormatException e) {
                    callback.failure(-1, "bad zoom value");
                }
                return;
            }

            callback.success("ok");
            switch (action) {
                case "singleplayer"   -> mc.setScreen(new SelectWorldScreen(this));
                case "multiplayer"    -> mc.setScreen(new JoinMultiplayerScreen(this));
                case "options"        -> mc.setScreen(new OptionsScreen(this, mc.options));
                // CEF держит non-daemon потоки и дочерний процесс jcef_helper — после
                // mc.stop() JVM ждёт их и не завершается. Принудительно убиваем процесс
                // (на титульном экране сохранять нечего).
                case "quit"           -> { LOGGER.info("[menu] quit -> halt(0)"); Runtime.getRuntime().halt(0); }
            }
        });
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (browser == null) return;
        int texId = browser.getRenderer().getTextureID();
        if (texId == 0) return;

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, texId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Matrix4f mat = guiGraphics.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(mat, 0,     0,      0).uv(0, 0).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, 0,     height, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, width, height, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
        buf.vertex(mat, width, 0,      0).uv(1, 0).color(255, 255, 255, 255).endVertex();
        tess.end();

        RenderSystem.enableDepthTest();
    }

    // ─── Input forwarding ────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (browser != null) { browser.sendMousePress(px(mx), py(my), btn); browser.setFocus(true); }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (browser != null) browser.sendMouseRelease(px(mx), py(my), btn);
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (browser != null) browser.sendMouseMove(px(mx), py(my));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (browser != null) browser.sendMouseWheel(px(mx), py(my), delta, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (browser != null) { browser.sendKeyPress(keyCode, (long) scanCode, modifiers); browser.setFocus(true); }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null) browser.sendKeyRelease(keyCode, (long) scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (browser != null && c != 0) browser.sendKeyTyped(c, modifiers);
        return true;
    }

    private int px(double guiX) { return (int)(guiX * minecraft.getWindow().getGuiScale()); }
    private int py(double guiY) { return (int)(guiY * minecraft.getWindow().getGuiScale()); }

    @Override
    public void tick() {
        super.tick();
        // Minecraft MusicManager запускает треки каждый тик — глушим пока открыто меню
        minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
