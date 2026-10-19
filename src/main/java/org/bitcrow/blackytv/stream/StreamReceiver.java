package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Frame;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StreamReceiver {

    private static final Map<String, LiveFeed> FEEDS = new ConcurrentHashMap<>();

    private StreamReceiver() {
    }

    public static Frame frame(String feed) {
        LiveFeed f = FEEDS.get(feed);
        return f == null ? null : f.frame;
    }

    public static void setVolume(String feed, float volume) {
        LiveFeed f = FEEDS.get(feed);
        if (f != null) f.volume = volume;
    }

    public static void watch(Set<String> wanted) {
        FEEDS.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            entry.getValue().shutdown();
            return true;
        });
        for (String feed : wanted) FEEDS.computeIfAbsent(feed, LiveFeed::new);
    }
}
