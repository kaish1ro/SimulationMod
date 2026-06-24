package com.eternity.simulation.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Крошечный localhost-сервер для раздачи аудио из папки simulation-music/.
 * Страница меню грузится из mod://, а Chromium блокирует file:// с чужого origin —
 * поэтому треки отдаём по http://127.0.0.1 (localhost считается доверенным,
 * CORS для <audio> не требуется). Поддерживает Range-запросы для перемотки.
 */
@OnlyIn(Dist.CLIENT)
public final class MusicServer {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("simulation.MusicServer");

    private static HttpServer server;
    private static int port = -1;
    private static Path root;

    private MusicServer() {}

    public static synchronized int start(Path musicDir) {
        root = musicDir;
        if (server != null) return port;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", MusicServer::handle);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "sim-music-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            port = server.getAddress().getPort();
            LOGGER.info("[music] HTTP server on 127.0.0.1:{}", port);
        } catch (IOException e) {
            LOGGER.error("[music] failed to start HTTP server: {}", e.getMessage());
            port = -1;
        }
        return port;
    }

    public static int getPort() { return port; }

    /** URL для одного файла, готовый для вставки в audio.src. */
    public static String urlFor(String fileName) {
        return "http://127.0.0.1:" + port + "/"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String raw = ex.getRequestURI().getPath();             // /track.mp3
            String name = URLDecoder.decode(raw.substring(1), StandardCharsets.UTF_8);
            Path f = root.resolve(name).normalize();
            // защита от path traversal — файл обязан лежать внутри папки
            if (!f.startsWith(root) || !Files.isRegularFile(f)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            long len = Files.size(f);
            ex.getResponseHeaders().set("Content-Type", contentType(name));
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");

            String range = ex.getRequestHeaders().getFirst("Range");
            if (range != null && range.startsWith("bytes=")) {
                long start, end;
                String[] parts = range.substring(6).split("-", 2);
                try {
                    start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0].trim());
                    end = (parts.length > 1 && !parts[1].isEmpty())
                            ? Long.parseLong(parts[1].trim()) : len - 1;
                } catch (NumberFormatException nfe) {
                    start = 0; end = len - 1;
                }
                if (end >= len) end = len - 1;
                if (start > end || start >= len) {
                    ex.getResponseHeaders().set("Content-Range", "bytes */" + len);
                    ex.sendResponseHeaders(416, -1);
                    return;
                }
                long clen = end - start + 1;
                ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + len);
                ex.sendResponseHeaders(206, ex.getRequestMethod().equals("HEAD") ? -1 : clen);
                if (!ex.getRequestMethod().equals("HEAD")) {
                    try (InputStream in = Files.newInputStream(f); OutputStream out = ex.getResponseBody()) {
                        in.skipNBytes(start);
                        copy(in, out, clen);
                    }
                }
            } else {
                ex.sendResponseHeaders(200, ex.getRequestMethod().equals("HEAD") ? -1 : len);
                if (!ex.getRequestMethod().equals("HEAD")) {
                    try (InputStream in = Files.newInputStream(f); OutputStream out = ex.getResponseBody()) {
                        in.transferTo(out);
                    }
                }
            }
        } catch (IOException io) {
            // клиент мог оборвать соединение при перемотке — это норма
        } finally {
            ex.close();
        }
    }

    private static void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buf = new byte[16384];
        long remaining = count;
        int r;
        while (remaining > 0 && (r = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
            out.write(buf, 0, r);
            remaining -= r;
        }
    }

    private static String contentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp3"))  return "audio/mpeg";
        if (n.endsWith(".ogg"))  return "audio/ogg";
        if (n.endsWith(".flac")) return "audio/flac";
        if (n.endsWith(".wav"))  return "audio/wav";
        return "application/octet-stream";
    }
}
