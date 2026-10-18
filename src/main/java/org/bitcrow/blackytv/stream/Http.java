package org.bitcrow.blackytv.stream;

import java.net.http.HttpClient;
import java.time.Duration;

final class Http {

    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private Http() {
    }
}
