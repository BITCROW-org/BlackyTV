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
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamSender {

    public enum Status { IDLE, SENDING, WRONG_PASSWORD, TOO_LARGE, RELAY_ERROR, UNREACHABLE }

    private static final float JPEG_QUALITY = 0.8f;
    private static final int AUDIO_BATCH = 2;
    private static final long RETRY_MS = 2_000;

    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> daemon(r, "BlackyTV-Encode"));
    private static final AtomicBoolean encoding = new AtomicBoolean();
    private static final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    private static volatile boolean sending;
    private static volatile Status status = Status.IDLE;
    private static volatile int lastResponseCode;
    private static volatile UploadStream upload;
    private static volatile Thread connection;

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

    public static synchronized void start() {
        if (sending) return;
        sending = true;
        status = Status.SENDING;
        connection = daemon(StreamSender::connect, "BlackyTV-Send");
        connection.start();
    }

    public static synchronized void stop() {
        if (!sending) return;
        sending = false;
        status = Status.IDLE;
        UploadStream u = upload;
        if (u != null) u.close();
        else connection.interrupt();
    }

    public static void pushVideo(Frame frame) {
        if (upload == null || !encoding.compareAndSet(false, true)) return;
        ENCODER.execute(() -> {
            try {
                UploadStream u = upload;
                if (u != null) u.video(jpeg(frame));
            } catch (IOException ignored) {
            } finally {
                encoding.set(false);
            }
        });
    }

    public static void pushAudio(byte[] pcm) {
        Pcm.gain(pcm, Settings.streamVolume / 100f);
        audioBuffer.write(pcm, 0, pcm.length);
        if (audioBuffer.size() < Pcm.FRAME_BYTES * AUDIO_BATCH) return;
        byte[] batch = audioBuffer.toByteArray();
        audioBuffer.reset();
        UploadStream u = upload;
        if (u != null) u.audio(batch);
    }

    private static void connect() {
        String base = Settings.relay + "/%s/" + Settings.channel;
        String key = Settings.password;
        UploadStream mine = null;
        try {
            while (isCurrent()) {
                try {
                    int claim = Http.CLIENT.send(HttpRequest.newBuilder(URI.create(base.formatted("claim")))
                            .timeout(Duration.ofSeconds(5)).header("X-Key", key)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode();
                    lastResponseCode = claim;
                    if (claim != 204) {
                        status = claim == 403 ? Status.WRONG_PASSWORD : Status.RELAY_ERROR;
                        Thread.sleep(RETRY_MS * 3);
                        continue;
                    }
                    status = Status.SENDING;
                    UploadStream u = mine = new UploadStream();
                    synchronized (StreamSender.class) {
                        if (!isCurrent()) break;
                        upload = u;
                    }
                    int code = Http.CLIENT.send(HttpRequest.newBuilder(URI.create(base.formatted("live")))
                            .header("X-Key", key)
                            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> u)).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode();
                    release(u);
                    if (!isCurrent()) break;
                    lastResponseCode = code;
                    status = code == 403 ? Status.WRONG_PASSWORD : Status.RELAY_ERROR;
                    Thread.sleep(RETRY_MS);
                } catch (IOException e) {
                    release(mine);
                    if (!isCurrent()) break;
                    status = Status.UNREACHABLE;
                    Thread.sleep(RETRY_MS);
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            release(mine);
        }
    }

    private static boolean isCurrent() {
        return sending && connection == Thread.currentThread();
    }

    private static synchronized void release(UploadStream u) {
        if (u == null) return;
        u.close();
        if (upload == u) upload = null;
    }

    static byte[] jpeg(Frame frame) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_BGR);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(frame.abgr(), 0, target, 0, target.length);
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
