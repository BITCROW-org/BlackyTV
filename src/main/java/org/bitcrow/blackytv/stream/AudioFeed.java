package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Pcm;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class AudioFeed extends FeedPoller {

    private static final int BUFFER_BYTES = Pcm.SAMPLE_RATE * 4 / 2;

    volatile float volume;
    private SourceDataLine line;

    AudioFeed(String feed) {
        super(feed, "audio");
        start();
    }

    @Override
    void received(byte[] pcm) throws LineUnavailableException {
        if (line == null) {
            line = AudioSystem.getSourceDataLine(Pcm.FORMAT);
            line.open(Pcm.FORMAT, BUFFER_BYTES);
            line.start();
        }
        float v = volume;
        if (v <= 0.001f) return;
        Pcm.gain(pcm, v);
        if (line.available() >= pcm.length) line.write(pcm, 0, pcm.length);
    }

    @Override
    void offline() {
        if (line == null) return;
        line.close();
        line = null;
    }
}
