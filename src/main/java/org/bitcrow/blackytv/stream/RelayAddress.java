package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.config.Settings;

public final class RelayAddress {

    public static final int DEFAULT_PORT = 8765;

    private RelayAddress() {
    }

    public static String resolve(String hostOrNull) {
        if (hostOrNull == null || hostOrNull.isBlank()) return Settings.relay;
        String url = hostOrNull.startsWith("http") ? hostOrNull : "http://" + hostOrNull;
        return url.substring(url.indexOf("//") + 2).contains(":") ? url : url + ":" + DEFAULT_PORT;
    }

    public static String feed(String relay, String channel) {
        return relay + "|" + channel;
    }
}
