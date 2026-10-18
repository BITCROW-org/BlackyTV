package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Frame;
import org.bitcrow.blackytv.capture.Pcm;
import org.bitcrow.blackytv.config.Settings;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class StreamSender {

    public enum Status { IDLE, SENDING, WRONG_PASSWORD, TOO_LARGE, RELAY_ERROR, UNREACHABLE }

    private static final float JPEG_QUALITY = 0.8f;
    private static final int AUDIO_BATCH = 5;
    private static final int MAX_QUEUED_AUDIO = 10;

    private static final ExecutorService VIDEO_OUT = Executors.newSingleThreadExecutor(r -> daemon(r, "BlackyTV-SendVideo"));
    private static final ExecutorService AUDIO_OUT = Executors.newSingleThreadExecutor(r -> daemon(r, "BlackyTV-SendAudio"));
    private static final AtomicBoolean videoBusy = new AtomicBoolean();
    private static final AtomicInteger audioQueued = new AtomicInteger();
    private static final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    private static volatile boolean sending;
    private static volatile Status status = Status.IDLE;
    private static volatile int lastResponseCode;

    private StreamSender() {
    }

    public static boolean isSending() {
        return sending;
    }

    public static Status status() {
        return status;
    }

    public static int lastResponseCode() {
        return lastResponseCode;
    }

    public static String ownFeed() {
        return RelayAddress.feed(Settings.relay, Settings.channel);
    }

    public static boolean isConfigured() {
        return !Settings.channel.isEmpty() && !Settings.password.isEmpty();
    }

    public static void start() {
        sending = true;
        status = Status.SENDING;
    }

    public static void stop() {
        if (!sending) return;
        sending = false;
        status = Status.IDLE;
        Http.CLIENT.sendAsync(request("push").DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }

    public static void pushVideo(Frame frame) {
        if (!videoBusy.compareAndSet(false, true)) return;
        VIDEO_OUT.execute(() -> {
            try {
                HttpResponse<Void> response = Http.CLIENT.send(
                        request("push").POST(HttpRequest.BodyPublishers.ofByteArray(jpeg(frame))).build(),
                        HttpResponse.BodyHandlers.discarding());
                lastResponseCode = response.statusCode();
                status = switch (response.statusCode()) {
                    case 204 -> Status.SENDING;
                    case 403 -> Status.WRONG_PASSWORD;
                    case 413 -> Status.TOO_LARGE;
                    default -> Status.RELAY_ERROR;
                };
            } catch (Exception e) {
                status = Status.UNREACHABLE;
            } finally {
                videoBusy.set(false);
            }
        });
    }

    public static void pushAudio(byte[] pcm) {
        Pcm.gain(pcm, Settings.streamVolume / 100f);
        audioBuffer.write(pcm, 0, pcm.length);
        if (audioBuffer.size() < Pcm.FRAME_BYTES * AUDIO_BATCH) return;
        byte[] batch = audioBuffer.toByteArray();
        audioBuffer.reset();
        if (audioQueued.get() > MAX_QUEUED_AUDIO) return;
        audioQueued.incrementAndGet();
        AUDIO_OUT.execute(() -> {
            try {
                Http.CLIENT.send(request("audio").POST(HttpRequest.BodyPublishers.ofByteArray(batch)).build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            } finally {
                audioQueued.decrementAndGet();
            }
        });
    }

    private static HttpRequest.Builder request(String kind) {
        return HttpRequest.newBuilder(URI.create(Settings.relay + "/" + kind + "/" + Settings.channel))
                .timeout(Duration.ofSeconds(3))
                .header("X-Key", Settings.password);
    }

    static byte[] jpeg(Frame frame) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.argb(), 0, frame.width());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
