package org.bitcrow.blackytv.control;

import org.bitcrow.blackytv.tv.Tv;

public final class ControlState {

    public record Pointer(Tv tv, double u, double v) {
    }

    private static volatile boolean active;
    private static volatile Pointer pointer;

    private ControlState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static Pointer pointer() {
        return active ? pointer : null;
    }

    static void start() {
        active = true;
        pointer = null;
    }

    static void stop() {
        active = false;
        pointer = null;
    }

    static void point(Pointer value) {
        pointer = value;
    }
}
