package org.bitcrow.blackytv.stream;

import java.net.http.HttpClient;
import java.time.Duration;

final class Http {

    static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    private Http() {
    }
}
