import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class BlackyTVRelay {

    private static final int DEFAULT_PORT = 8765;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MIN_KEY_LENGTH = 4;
    private static final int AUDIO_HISTORY = 20;
    private static final long POLL_TIMEOUT_MS = 10_000;
    private static final long STALE_MS = 15_000;
    private static final String CHANNEL_PATTERN = "[a-z0-9_-]{1,32}";
    private static final Path CLAIMS_FILE = Path.of("blackytv-kanaele.properties");

    private record Frame(long sequence, byte[] jpeg, long receivedAt) {
    }

    private record AudioChunk(long sequence, byte[] pcm) {
    }

    private static final Map<String, Frame> frames = new ConcurrentHashMap<>();
    private static final Map<String, Deque<AudioChunk>> audio = new ConcurrentHashMap<>();
    private static final Properties claims = new Properties();
    private static final Object update = new Object();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        loadClaims();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/push/", BlackyTVRelay::handlePush);
        server.createContext("/frame/", BlackyTVRelay::handleFrame);
        server.createContext("/audio/", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) handleAudioPush(exchange);
            else handleAudioPoll(exchange);
        });
        server.start();
        System.out.println("BlackyTV Relay laeuft auf Port " + port + " (" + claims.size() + " Kanaele vergeben)");
    }

    private static void handlePush(HttpExchange exchange) throws IOException {
        try (exchange) {
            String channel = channel(exchange, "/push/");
            if (channel == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!authorized(exchange, channel)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (exchange.getRequestMethod().equals("DELETE")) {
                frames.remove(channel);
                audio.remove(channel);
            } else {
                byte[] jpeg = readBody(exchange);
                if (jpeg == null) {
                    exchange.sendResponseHeaders(413, -1);
                    return;
                }
                frames.compute(channel, (c, old) -> new Frame(old == null ? 1 : old.sequence() + 1, jpeg, System.currentTimeMillis()));
            }
            notifyWaiting();
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void handleAudioPush(HttpExchange exchange) throws IOException {
        try (exchange) {
            String channel = channel(exchange, "/audio/");
            if (channel == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!authorized(exchange, channel)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            byte[] pcm = readBody(exchange);
            if (pcm == null) {
                exchange.sendResponseHeaders(413, -1);
                return;
            }
            Deque<AudioChunk> chunks = audio.computeIfAbsent(channel, c -> new ArrayDeque<>());
            synchronized (chunks) {
                chunks.addLast(new AudioChunk(chunks.isEmpty() ? 1 : chunks.peekLast().sequence() + 1, pcm));
                while (chunks.size() > AUDIO_HISTORY) chunks.removeFirst();
            }
            notifyWaiting();
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void handleFrame(HttpExchange exchange) throws IOException {
        try (exchange) {
            String channel = channel(exchange, "/frame/");
            if (channel == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            long after = after(exchange);
            long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
            while (true) {
                Frame frame = frames.get(channel);
                if (frame != null && System.currentTimeMillis() - frame.receivedAt() > STALE_MS) {
                    frames.remove(channel, frame);
                    audio.remove(channel);
                    frame = null;
                }
                if (frame == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                if (frame.sequence() > after) {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    send(exchange, frame.sequence(), frame.jpeg());
                    return;
                }
                if (!waitForUpdate(deadline)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
            }
        }
    }

    private static void handleAudioPoll(HttpExchange exchange) throws IOException {
        try (exchange) {
            String channel = channel(exchange, "/audio/");
            if (channel == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            long after = after(exchange);
            long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
            while (true) {
                if (!frames.containsKey(channel)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Deque<AudioChunk> chunks = audio.get(channel);
                if (chunks != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    long last = after;
                    synchronized (chunks) {
                        Iterable<AudioChunk> pending = after == 0 && !chunks.isEmpty() ? List.of(chunks.peekLast()) : chunks;
                        for (AudioChunk chunk : pending) {
                            if (chunk.sequence() <= after) continue;
                            out.writeBytes(chunk.pcm());
                            last = chunk.sequence();
                        }
                    }
                    if (out.size() > 0) {
                        send(exchange, last, out.toByteArray());
                        return;
                    }
                }
                if (!waitForUpdate(deadline)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
            }
        }
    }

    private static boolean authorized(HttpExchange exchange, String channel) {
        String key = exchange.getRequestHeaders().getFirst("X-Key");
        if (key == null || key.length() < MIN_KEY_LENGTH) return false;
        String hash = sha256(key);
        synchronized (claims) {
            String owner = claims.getProperty(channel);
            if (owner != null)
                return MessageDigest.isEqual(owner.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
            claims.setProperty(channel, hash);
            saveClaims();
            System.out.println("Kanal " + channel + " ist jetzt vergeben");
            return true;
        }
    }

    private static void loadClaims() throws IOException {
        if (!Files.exists(CLAIMS_FILE)) return;
        try (Reader reader = Files.newBufferedReader(CLAIMS_FILE)) {
            claims.load(reader);
        }
    }

    private static void saveClaims() {
        try (Writer writer = Files.newBufferedWriter(CLAIMS_FILE)) {
            claims.store(writer, "BlackyTV Kanaele (Kanal = SHA-256 vom Passwort). Zeile loeschen = Kanal frei");
        } catch (IOException e) {
            System.err.println("Kanaele konnten nicht gespeichert werden: " + e);
        }
    }

    private static String channel(HttpExchange exchange, String prefix) {
        String channel = exchange.getRequestURI().getPath().substring(prefix.length()).toLowerCase();
        return channel.matches(CHANNEL_PATTERN) ? channel : null;
    }

    private static long after(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("after=")) return 0;
        try {
            return Long.parseLong(query.substring(6));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            return body.length == 0 || body.length > MAX_BODY_BYTES ? null : body;
        }
    }

    private static void send(HttpExchange exchange, long sequence, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("X-Seq", String.valueOf(sequence));
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void notifyWaiting() {
        synchronized (update) {
            update.notifyAll();
        }
    }

    private static boolean waitForUpdate(long deadline) {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) return false;
        synchronized (update) {
            try {
                update.wait(Math.min(left, 1000));
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
