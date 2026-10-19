package org.bitcrow.blackytv.stream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

final class UploadStream extends InputStream {

    static final byte VIDEO = 1, AUDIO = 2, PING = 3;

    private static final long PING_INTERVAL_MS = 5_000;
    private static final int MAX_QUEUED_AUDIO = 10;

    private final ArrayDeque<byte[]> audio = new ArrayDeque<>();
    private byte[] video;
    private ByteBuffer current = ByteBuffer.allocate(0);
    private boolean closed;

    synchronized void video(byte[] jpeg) {
        video = jpeg;
        notifyAll();
    }

    synchronized void audio(byte[] pcm) {
        if (audio.size() >= MAX_QUEUED_AUDIO) audio.removeFirst();
        audio.addLast(pcm);
        notifyAll();
    }

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    @Override
    public int read() {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
        if (len == 0) return 0;
        while (!current.hasRemaining()) {
            if (closed) return -1;
            current = next();
        }
        int n = Math.min(len, current.remaining());
        current.get(b, off, n);
        return n;
    }

    private ByteBuffer next() {
        long deadline = System.currentTimeMillis() + PING_INTERVAL_MS;
        while (!closed) {
            if (!audio.isEmpty()) return message(AUDIO, audio.removeFirst());
            if (video != null) {
                byte[] jpeg = video;
                video = null;
                return message(VIDEO, jpeg);
            }
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return message(PING, new byte[0]);
            try {
                wait(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                closed = true;
            }
        }
        return ByteBuffer.allocate(0);
    }

    static ByteBuffer message(byte type, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(5 + payload.length);
        buffer.put(type).putInt(payload.length).put(payload).flip();
        return buffer;
    }
}
