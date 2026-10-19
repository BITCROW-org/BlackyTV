package org.bitcrow.blackytv.capture;

public record Frame(int[] abgr, int width, int height) {

    public static Frame fromArgb(int[] pixels, int width, int height) {
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            pixels[i] = 0xFF000000 | (p & 0xFF00) | (p & 0xFF) << 16 | (p >> 16 & 0xFF);
        }
        return new Frame(pixels, width, height);
    }

    public static Frame fromBgrBytes(byte[] bgr, int width, int height) {
        int[] pixels = new int[width * height];
        for (int i = 0, b = 0; i < pixels.length; i++, b += 3)
            pixels[i] = 0xFF000000 | (bgr[b] & 0xFF) << 16 | (bgr[b + 1] & 0xFF) << 8 | (bgr[b + 2] & 0xFF);
        return new Frame(pixels, width, height);
    }

    public float aspect() {
        return width / (float) height;
    }
}
