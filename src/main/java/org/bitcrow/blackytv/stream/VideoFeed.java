package org.bitcrow.blackytv.stream;

import org.bitcrow.blackytv.capture.Frame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class VideoFeed extends FeedPoller {

    volatile Frame frame;

    VideoFeed(String feed) {
        super(feed, "frame");
        start();
    }

    @Override
    void received(byte[] body) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
        if (image == null) return;
        int w = image.getWidth(), h = image.getHeight();
        frame = Frame.of(image.getRGB(0, 0, w, h, null, 0, w), w, h);
    }

    @Override
    void offline() {
        frame = null;
    }
}
