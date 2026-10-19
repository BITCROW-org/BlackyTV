package org.bitcrow.blackytv.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Settings {

    private static final Logger LOG = LoggerFactory.getLogger("BlackyTV");

    public static final String DEFAULT_RELAY = "http://localhost:8765";

    public static volatile String source;
    public static volatile int fps = 30;
    public static volatile int quality = 1280;
    public static volatile boolean keepAwake;
    public static volatile boolean showCursor = true;
    public static volatile boolean tapControl = true;
    public static volatile int volume = 100;
    public static volatile int streamVolume = 100;
    public static volatile String relay = DEFAULT_RELAY;
    public static volatile String channel = "";
    public static volatile String password = "";

    private Settings() {
    }

    public static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file())) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        source = p.getProperty("source");
        fps = number(p, "fps", fps, 1, 60);
        quality = number(p, "maxw", quality, 320, 1920);
        keepAwake = Boolean.parseBoolean(p.getProperty("awake", "false"));
        showCursor = Boolean.parseBoolean(p.getProperty("cursor", "true"));
        tapControl = Boolean.parseBoolean(p.getProperty("tap", "true"));
        volume = number(p, "volume", volume, 0, 200);
        streamVolume = number(p, "sendVolume", streamVolume, 0, 200);
        relay = p.getProperty("relay", relay);
        channel = p.getProperty("channel", "");
        password = p.getProperty("key", "");
    }

    public static void save() {
        Properties p = new Properties();
        if (source != null) p.setProperty("source", source);
        p.setProperty("fps", String.valueOf(fps));
        p.setProperty("maxw", String.valueOf(quality));
        p.setProperty("awake", String.valueOf(keepAwake));
        p.setProperty("cursor", String.valueOf(showCursor));
        p.setProperty("tap", String.valueOf(tapControl));
        p.setProperty("volume", String.valueOf(volume));
        p.setProperty("sendVolume", String.valueOf(streamVolume));
        p.setProperty("relay", relay);
        p.setProperty("channel", channel);
        p.setProperty("key", password);
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "BlackyTV");
        } catch (IOException e) {
            LOG.warn("Could not save config", e);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("blackytv.properties");
    }

    private static int number(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, String.valueOf(fallback)))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
