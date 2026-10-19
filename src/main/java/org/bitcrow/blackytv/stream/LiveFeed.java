package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Frame;
import org.bitcrow.blackytv.capture.Pcm;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

final class LiveFeed extends Thread {

    private static final int MAX_MESSAGE = 2 * 1024 * 1024;
    private static final int AUDIO_BUFFER_BYTES = Pcm.SAMPLE_RATE * 4 / 2;
    private static final long SILENCE_MS = 15_000;
    private static final long RETRY_MS = 2_000;
    private static final Timer WATCHDOG = new Timer("BlackyTV-Watchdog", true);

    volatile Frame frame;
    volatile float volume;

    private final URI url;
    private final TimerTask watchdog;
    private volatile boolean stopped;
    private volatile long lastData;
    private SourceDataLine line;
    private final ImageReader reader = ImageIO.getImageReadersByFormatName("jpeg").next();
    private final Frame.Pool pool = new Frame.Pool();
    private BufferedImage decoded;

    LiveFeed(String feed) {
        super("BlackyTV-Watch " + feed);
        String[] parts = feed.split("\\|");
        url = URI.create(parts[0] + "/watch/" + parts[1]);
        setDaemon(true);
        watchdog = new TimerTask() {
            @Override
            public void run() {
                if (lastData != 0 && System.currentTimeMillis() - lastData > SILENCE_MS) {
                    lastData = 0;
                    interrupt();
                }
            }
        };
        WATCHDOG.schedule(watchdog, 5_000, 5_000);
        start();
    }

    void shutdown() {
        stopped = true;
        watchdog.cancel();
        interrupt();
    }

    @Override
    public void run() {
        while (!stopped) {
            try {
                watch();
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException | IOException ignored) {
                Thread.interrupted();
            } finally {
                offline();
            }
        }
    }

    private void watch() throws IOException, InterruptedException {
        HttpResponse<InputStream> response = Http.CLIENT.send(
                HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(response.body(), 64 * 1024))) {
            if (response.statusCode() != 200) return;
            lastData = System.currentTimeMillis();
            while (!stopped) {
                byte type = in.readByte();
                int length = in.readInt();
                if (length < 0 || length > MAX_MESSAGE) return;
                byte[] payload = in.readNBytes(length);
                if (payload.length < length) return;
                lastData = System.currentTimeMillis();
                if (type == UploadStream.VIDEO) video(payload);
                else if (type == UploadStream.AUDIO) audio(payload);
            }
        }
    }

    private void video(byte[] jpeg) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            reader.setInput(input, true, true);
            int w = reader.getWidth(0), h = reader.getHeight(0);
            if (decoded == null || decoded.getWidth() != w || decoded.getHeight() != h)
                decoded = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setDestination(decoded);
            reader.read(0, param);
        }
        byte[] bgr = ((DataBufferByte) decoded.getRaster().getDataBuffer()).getData();
        frame = Frame.fromBgrBytes(bgr, decoded.getWidth(), decoded.getHeight(), pool.take(decoded.getWidth() * decoded.getHeight()));
    }

    private void audio(byte[] pcm) {
        float v = volume;
        if (v <= 0.001f) return;
        try {
            if (line == null) {
                line = AudioSystem.getSourceDataLine(Pcm.FORMAT);
                line.open(Pcm.FORMAT, AUDIO_BUFFER_BYTES);
                line.start();
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            return;
        }
        Pcm.gain(pcm, v);
        if (line.available() >= pcm.length) line.write(pcm, 0, pcm.length);
    }

    private void offline() {
        lastData = 0;
        frame = null;
        if (line != null) {
            line.close();
            line = null;
        }
    }
}
