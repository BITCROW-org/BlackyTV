package org.bitcrow.blackytv.capture;

import javax.sound.sampled.AudioFormat;

public final class Pcm {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_BYTES = 960 * 4;
    public static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);

    private Pcm() {
    }

    public static void gain(byte[] pcm, float factor) {
        if (Math.abs(factor - 1) < 0.001f) return;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (int) ((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)) * factor);
            s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            pcm[i] = (byte) s;
            pcm[i + 1] = (byte) (s >> 8);
        }
    }
}
