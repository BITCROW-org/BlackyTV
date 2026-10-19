import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
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
import java.util.ArrayList;
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
    private static final long PING_INTERVAL_MS = 5_000;
    private static final long STALE_MS = 15_000;
    private static final String CHANNEL_PATTERN = "[a-z0-9_-]{1,32}";
    private static final Path CLAIMS_FILE = Path.of("blackytv-kanaele.properties");

    private static final byte TYPE_VIDEO = 1;
    private static final byte TYPE_AUDIO = 2;
    private static final byte TYPE_PING = 3;

    private record Frame(long sequence, byte[] jpeg) {
    }

    private record AudioChunk(long sequence, byte[] pcm) {
    }

    private static final class Channel {
        private Frame frame;
        private final Deque<AudioChunk> audio = new ArrayDeque<>();
        private long lastSeen;
        private long frameCounter;
        private long audioCounter;

        synchronized boolean isLive() {
            return frame != null && System.currentTimeMillis() - lastSeen < STALE_MS;
        }

        synchronized void putFrame(byte[] jpeg) {
            frame = new Frame(++frameCounter, jpeg);
            lastSeen = System.currentTimeMillis();
            notifyAll();
        }

        synchronized void putAudio(byte[] pcm) {
            audio.addLast(new AudioChunk(++audioCounter, pcm));
            while (audio.size() > AUDIO_HISTORY) audio.removeFirst();
            lastSeen = System.currentTimeMillis();
            notifyAll();
        }

        synchronized void ping() {
            lastSeen = System.currentTimeMillis();
        }

        synchronized void end() {
            frame = null;
            audio.clear();
            notifyAll();
        }

        synchronized Frame frameAfter(long sequence) {
            return frame != null && frame.sequence() > sequence ? frame : null;
        }

        synchronized List<AudioChunk> audioAfter(long sequence) {
            List<AudioChunk> out = new ArrayList<>();
            if (sequence == 0 && !audio.isEmpty()) out.add(audio.peekLast());
            else for (AudioChunk chunk : audio) if (chunk.sequence() > sequence) out.add(chunk);
            return out;
        }

        synchronized void await(long millis) throws InterruptedException {
            wait(millis);
        }
    }

    private static final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private static final Properties claims = new Properties();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        loadClaims();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/claim/", BlackyTVRelay::handleClaim);
        server.createContext("/live/", BlackyTVRelay::handleLive);
        server.createContext("/watch/", BlackyTVRelay::handleWatch);
        server.createContext("/push/", BlackyTVRelay::handlePush);
        server.createContext("/frame/", BlackyTVRelay::handleFrame);
        server.createContext("/audio/", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) handleAudioPush(exchange);
            else handleAudioPoll(exchange);
        });
        server.start();
        System.out.println("BlackyTV Relay laeuft auf Port " + port + " (" + claims.size() + " Kanaele vergeben)");
    }

    private static Channel channelFor(String name) {
        return channels.computeIfAbsent(name, n -> new Channel());
    }

    private static void handleClaim(HttpExchange exchange) throws IOException {
        try (exchange) {
            String channel = channel(exchange, "/claim/");
            if (channel == null) exchange.sendResponseHeaders(400, -1);
            else exchange.sendResponseHeaders(authorized(exchange, channel) ? 204 : 403, -1);
        }
    }

    private static void handleLive(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/live/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!authorized(exchange, name)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            Channel channel = channelFor(name);
            System.out.println("Kanal " + name + " sendet");
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(exchange.getRequestBody()))) {
                while (true) {
                    byte type = in.readByte();
                    int length = in.readInt();
                    if (length < 0 || length > MAX_BODY_BYTES) break;
                    byte[] payload = in.readNBytes(length);
                    if (payload.length < length) break;
                    switch (type) {
                        case TYPE_VIDEO -> channel.putFrame(payload);
                        case TYPE_AUDIO -> channel.putAudio(payload);
                        default -> channel.ping();
                    }
                }
            } catch (EOFException ignored) {
            } catch (IOException e) {
                System.out.println("Kanal " + name + " Verbindung weg: " + e.getMessage());
            } finally {
                channel.end();
                System.out.println("Kanal " + name + " aus");
            }
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void handleWatch(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/watch/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Channel channel = channels.get(name);
            if (channel == null || !channel.isLive()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(exchange.getResponseBody(), 64 * 1024));
            long videoSeq = 0, audioSeq = 0, lastWrite = System.currentTimeMillis();
            try {
                while (channel.isLive()) {
                    boolean wrote = false;
                    for (AudioChunk chunk : channel.audioAfter(audioSeq)) {
                        write(out, TYPE_AUDIO, chunk.pcm());
                        audioSeq = chunk.sequence();
                        wrote = true;
                    }
                    Frame frame = channel.frameAfter(videoSeq);
                    if (frame != null) {
                        write(out, TYPE_VIDEO, frame.jpeg());
                        videoSeq = frame.sequence();
                        wrote = true;
                    }
                    long now = System.currentTimeMillis();
                    if (!wrote && now - lastWrite >= PING_INTERVAL_MS) {
                        write(out, TYPE_PING, new byte[0]);
                        wrote = true;
                    }
                    if (wrote) {
                        out.flush();
                        lastWrite = now;
                    } else {
                        channel.await(1000);
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }
    }

    private static void write(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static void handlePush(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/push/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!authorized(exchange, name)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (exchange.getRequestMethod().equals("DELETE")) {
                channelFor(name).end();
            } else {
                byte[] jpeg = readBody(exchange);
                if (jpeg == null) {
                    exchange.sendResponseHeaders(413, -1);
                    return;
                }
                channelFor(name).putFrame(jpeg);
            }
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void handleAudioPush(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/audio/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!authorized(exchange, name)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            byte[] pcm = readBody(exchange);
            if (pcm == null) {
                exchange.sendResponseHeaders(413, -1);
                return;
            }
            channelFor(name).putAudio(pcm);
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void handleFrame(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/frame/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Channel channel = channels.get(name);
            long after = after(exchange);
            long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
            while (true) {
                if (channel == null || !channel.isLive()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Frame frame = channel.frameAfter(after);
                if (frame != null) {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    send(exchange, frame.sequence(), frame.jpeg());
                    return;
                }
                if (!waitFor(channel, deadline)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
            }
        }
    }

    private static void handleAudioPoll(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = channel(exchange, "/audio/");
            if (name == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            Channel channel = channels.get(name);
            long after = after(exchange);
            long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
            while (true) {
                if (channel == null || !channel.isLive()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                List<AudioChunk> chunks = channel.audioAfter(after);
                if (!chunks.isEmpty()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    for (AudioChunk chunk : chunks) out.writeBytes(chunk.pcm());
                    send(exchange, chunks.get(chunks.size() - 1).sequence(), out.toByteArray());
                    return;
                }
                if (!waitFor(channel, deadline)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
            }
        }
    }

    private static boolean waitFor(Channel channel, long deadline) {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) return false;
        try {
            channel.await(Math.min(left, 1000));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
