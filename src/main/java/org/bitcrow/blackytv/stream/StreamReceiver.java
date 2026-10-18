package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Frame;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StreamReceiver {

    private record Feed(VideoFeed video, AudioFeed audio) {
        void stop() {
            video.interrupt();
            audio.interrupt();
        }
    }

    private static final Map<String, Feed> FEEDS = new ConcurrentHashMap<>();

    private StreamReceiver() {
    }

    public static Frame frame(String feed) {
        Feed f = FEEDS.get(feed);
        return f == null ? null : f.video().frame;
    }

    public static void setVolume(String feed, float volume) {
        Feed f = FEEDS.get(feed);
        if (f != null) f.audio().volume = volume;
    }

    public static void watch(Set<String> wanted) {
        FEEDS.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            entry.getValue().stop();
            return true;
        });
        for (String feed : wanted) FEEDS.computeIfAbsent(feed, f -> new Feed(new VideoFeed(f), new AudioFeed(f)));
    }
}
