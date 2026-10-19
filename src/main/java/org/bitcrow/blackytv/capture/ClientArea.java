package org.bitcrow.blackytv.capture;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

public record ClientArea(int screenX, int screenY, int width, int height,
                         int offsetX, int offsetY, int windowWidth, int windowHeight) {

    public static ClientArea of(Pointer hwnd) {
        Memory window = new Memory(16);
        Memory client = new Memory(16);
        Memory origin = new Memory(8);
        origin.clear();
        if (Win32.user32("GetWindowRect", hwnd, window) == 0) return null;
        if (Win32.user32("GetClientRect", hwnd, client) == 0) return null;
        if (Win32.user32("ClientToScreen", hwnd, origin) == 0) return null;

        int windowLeft = window.getInt(0), windowTop = window.getInt(4);
        int windowWidth = window.getInt(8) - windowLeft, windowHeight = window.getInt(12) - windowTop;
        int screenX = origin.getInt(0), screenY = origin.getInt(4);
        int offsetX = Math.max(0, screenX - windowLeft), offsetY = Math.max(0, screenY - windowTop);
        int width = Math.min(client.getInt(8), windowWidth - offsetX);
        int height = Math.min(client.getInt(12), windowHeight - offsetY);
        if (width <= 0 || height <= 0) return null;
        return new ClientArea(screenX, screenY, width, height, offsetX, offsetY, windowWidth, windowHeight);
    }
}
