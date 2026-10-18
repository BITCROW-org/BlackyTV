package org.bitcrow.blackytv.capture;

import com.sun.jna.Pointer;

import static org.bitcrow.blackytv.capture.Win32.*;

final class WindowKeeper {

    private static final long WS_EX_TOPMOST = 0x8;
    private static final long WS_EX_TRANSPARENT = 0x20;
    private static final long WS_EX_LAYERED = 0x80000;
    private static final int LWA_ALPHA = 2;
    private static final int RDW_REDRAW_ALL = 0x485;

    private static Pointer kept;
    private static long originalStyle;

    private WindowKeeper() {
    }

    static synchronized boolean isKept(Pointer hwnd) {
        return hwnd != null && hwnd.equals(kept);
    }

    static synchronized void keep(Pointer hwnd) {
        if (isKept(hwnd)) return;
        restore();
        if (hwnd == null) return;
        originalStyle = user32Long("GetWindowLongPtrW", hwnd, GWL_EXSTYLE);
        user32Long("SetWindowLongPtrW", hwnd, GWL_EXSTYLE, originalStyle | WS_EX_LAYERED | WS_EX_TRANSPARENT);
        user32("SetLayeredWindowAttributes", hwnd, 0, (byte) 1, LWA_ALPHA);
        user32("SetWindowPos", hwnd, HWND_TOP_MOST, 0, 0, 0, 0, SWP_NO_SIZE_MOVE_ACTIVATE);
        kept = hwnd;
    }

    static synchronized void release() {
        restore();
    }

    private static void restore() {
        if (kept == null) return;
        if (user32("IsWindow", kept) != 0) {
            user32Long("SetWindowLongPtrW", kept, GWL_EXSTYLE, originalStyle);
            if ((originalStyle & WS_EX_TOPMOST) == 0)
                user32("SetWindowPos", kept, HWND_NOT_TOP_MOST, 0, 0, 0, 0, SWP_NO_SIZE_MOVE_ACTIVATE);
            user32("RedrawWindow", kept, null, null, RDW_REDRAW_ALL);
        }
        kept = null;
    }
}
