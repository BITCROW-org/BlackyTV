package org.bitcrow.blackytv.capture;

public record Frame(int[] argb, int[] abgr, int width, int height) {

    public static Frame of(int[] argb, int width, int height) {
        int[] abgr = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i] | 0xFF000000;
            argb[i] = p;
            abgr[i] = p & 0xFF00FF00 | (p & 0xFF) << 16 | (p >> 16 & 0xFF);
        }
        return new Frame(argb, abgr, width, height);
    }

    public float aspect() {
        return width / (float) height;
    }
}
