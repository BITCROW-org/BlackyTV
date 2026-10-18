package org.bitcrow.blackytv.capture;

import com.sun.jna.Pointer;

public record CaptureSource(String name, Pointer hwnd, int x, int y, int width, int height) {

    public boolean isWindow() {
        return hwnd != null;
    }

    public boolean sameAs(CaptureSource other) {
        if (other == null) return false;
        return isWindow() ? hwnd.equals(other.hwnd) : name.equals(other.name);
    }
}
