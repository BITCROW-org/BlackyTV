package org.bitcrow.blackytv.stream;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

abstract class FeedPoller extends Thread {

    private final String url;
    private long sequence;

    FeedPoller(String feed, String kind) {
        super("BlackyTV-" + kind + " " + feed);
        String[] parts = feed.split("\\|");
        url = parts[0] + "/" + kind + "/" + parts[1];
        setDaemon(true);
    }

    abstract void received(byte[] body) throws Exception;

    abstract void offline();

    @Override
    public void run() {
        try {
            while (!isInterrupted()) poll();
        } catch (InterruptedException ignored) {
        } finally {
            offline();
        }
    }

    private void poll() throws InterruptedException {
        try {
            HttpResponse<byte[]> response = Http.CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url + "?after=" + sequence)).timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            switch (response.statusCode()) {
                case 200 -> {
                    sequence = Long.parseLong(response.headers().firstValue("X-Seq").orElse("0"));
                    received(response.body());
                }
                case 204 -> {
                }
                case 404 -> {
                    offline();
                    sequence = 0;
                    Thread.sleep(2000);
                }
                default -> Thread.sleep(2000);
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            sequence = 0;
            offline();
            Thread.sleep(3000);
        }
    }
}
