package org.bitcrow.blackytv.capture;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.bitcrow.blackytv.config.Settings;
import org.bitcrow.blackytv.control.ControlState;
import org.bitcrow.blackytv.control.CursorOverlay;
import org.bitcrow.blackytv.stream.StreamSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.bitcrow.blackytv.capture.Win32.*;

public final class ScreenCapture {

    private static final Logger LOG = LoggerFactory.getLogger("BlackyTV");

    private static final int WS_EX_TOOLWINDOW = 0x80;
    private static final int DWMWA_CLOAKED = 14;
    private static final int SW_SHOW_NO_ACTIVATE = 4;
    private static final int HALFTONE = 4;

    private static final Frame.Pool POOL = new Frame.Pool();

    private static volatile CaptureSource source;
    private static volatile Frame frame;
    private static volatile boolean wanted;

    private ScreenCapture() {
    }

    public static boolean isSupported() {
        return isWindows();
    }

    public static CaptureSource source() {
        return source;
    }

    public static Frame frame() {
        return source == null ? null : frame;
    }

    public static void setWanted(boolean value) {
        wanted = value;
    }

    public static void select(CaptureSource s) {
        source = s;
        Settings.source = s == null ? null : s.name();
        frame = null;
    }

    public static void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(WindowKeeper::release));
        Thread thread = new Thread(ScreenCapture::run, "BlackyTV-Capture");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        String saved = Settings.source;
        if (saved != null) source = list().stream().filter(s -> s.name().equals(saved)).findFirst().orElse(null);

        while (true) {
            long started = System.currentTimeMillis();
            CaptureSource s = source;
            boolean active = wanted || StreamSender.isSending();

            try {
                WindowKeeper.keep(active && Settings.keepAwake && s != null && s.isWindow() ? s.hwnd() : null);
            } catch (Throwable e) {
                LOG.warn("Could not keep window awake", e);
            }

            if (active && s != null) {
                try {
                    Frame f = grab(s);
                    if (f != null && s == source) {
                        frame = f;
                        if (StreamSender.isSending()) StreamSender.pushVideo(f);
                    }
                } catch (Throwable e) {
                    LOG.warn("Capture failed", e);
                }
            }

            long wait = active ? 1000 / Settings.fps - (System.currentTimeMillis() - started) : 250;
            try {
                Thread.sleep(Math.max(1, wait));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public interface WindowCallback extends Callback {
        int invoke(Pointer hwnd, Pointer lParam);
    }

    public interface MonitorCallback extends Callback {
        int invoke(Pointer monitor, Pointer hdc, Pointer rect, Pointer lParam);
    }

    private static List<CaptureSource> found;
    private static final Memory TITLE = new Memory(1024);

    private static final MonitorCallback ON_MONITOR = (monitor, hdc, rect, lParam) -> {
        int x = rect.getInt(0), y = rect.getInt(4), w = rect.getInt(8) - x, h = rect.getInt(12) - y;
        found.add(new CaptureSource("Monitor " + (found.size() + 1) + " (" + w + "x" + h + ")", null, x, y, w, h));
        return 1;
    };

    private static final WindowCallback ON_WINDOW = (hwnd, lParam) -> {
        if (user32("IsWindowVisible", hwnd) == 0) return 1;
        if ((user32("GetWindowLongW", hwnd, GWL_EXSTYLE) & WS_EX_TOOLWINDOW) != 0) return 1;
        IntByReference cloaked = new IntByReference();
        try {
            function("dwmapi", "DwmGetWindowAttribute").invokeInt(new Object[]{hwnd, DWMWA_CLOAKED, cloaked, 4});
        } catch (Throwable ignored) {
        }
        if (cloaked.getValue() != 0) return 1;
        if (user32("GetWindowTextW", hwnd, TITLE, 512) == 0) return 1;
        String title = TITLE.getWideString(0);
        if (!title.equals("Program Manager")) found.add(new CaptureSource(title, hwnd, 0, 0, 0, 0));
        return 1;
    };

    public static synchronized List<CaptureSource> list() {
        found = new ArrayList<>();
        if (!isWindows()) return found;
        user32("EnumDisplayMonitors", null, null, ON_MONITOR, null);
        user32("EnumWindows", ON_WINDOW, null);
        return found;
    }

    private static Pointer windowDc, windowBitmap, outDc, outBitmap, outPixels;
    private static int windowWidth, windowHeight, outWidth, outHeight;

    private static Frame grab(CaptureSource s) {
        Pointer screen = user32Pointer("GetDC", (Object) null);
        try {
            Pointer from;
            int sx = 0, sy = 0, sw, sh;

            if (s.isWindow()) {
                if (user32("IsWindow", s.hwnd()) == 0) return null;
                if (user32("IsIconic", s.hwnd()) != 0) {
                    user32("ShowWindow", s.hwnd(), SW_SHOW_NO_ACTIVATE);
                    if (!WindowKeeper.isKept(s.hwnd()))
                        user32("SetWindowPos", s.hwnd(), HWND_BOTTOM, 0, 0, 0, 0, SWP_NO_SIZE_MOVE_ACTIVATE);
                    return null;
                }
                ClientArea area = ClientArea.of(s.hwnd());
                if (area == null) return null;
                if (windowDc == null || windowWidth != area.windowWidth() || windowHeight != area.windowHeight()) {
                    free(windowDc, windowBitmap);
                    windowDc = gdi32Pointer("CreateCompatibleDC", screen);
                    windowBitmap = gdi32Pointer("CreateCompatibleBitmap", screen, area.windowWidth(), area.windowHeight());
                    gdi32Pointer("SelectObject", windowDc, windowBitmap);
                    windowWidth = area.windowWidth();
                    windowHeight = area.windowHeight();
                }
                if (user32("PrintWindow", s.hwnd(), windowDc, 2) == 0) return null;
                from = windowDc;
                sx = area.offsetX();
                sy = area.offsetY();
                sw = area.width();
                sh = area.height();
            } else {
                from = screen;
                sx = s.x();
                sy = s.y();
                sw = s.width();
                sh = s.height();
            }

            int w = Math.min(Settings.quality, sw);
            int h = Math.max(1, Math.round(sh * (w / (float) sw)));
            if (outDc == null || outWidth != w || outHeight != h) createOutput(screen, w, h);

            if (w == sw && h == sh) gdi32("BitBlt", outDc, 0, 0, w, h, from, sx, sy, SRCCOPY);
            else gdi32("StretchBlt", outDc, 0, 0, w, h, from, sx, sy, sw, sh, SRCCOPY);
            gdi32("GdiFlush");

            int[] pixels = POOL.take(w * h);
            outPixels.read(0, pixels, 0, pixels.length);
            ControlState.Pointer pointer = ControlState.pointer();
            if (pointer != null && Settings.showCursor && StreamSender.isSending())
                CursorOverlay.draw(pixels, w, h, pointer.u(), pointer.v());
            return Frame.fromArgb(pixels, w, h);
        } finally {
            user32("ReleaseDC", null, screen);
        }
    }

    private static void createOutput(Pointer screen, int w, int h) {
        free(outDc, outBitmap);
        Memory info = new Memory(44);
        info.clear();
        info.setInt(0, 40);
        info.setInt(4, w);
        info.setInt(8, -h);
        info.setShort(12, (short) 1);
        info.setShort(14, (short) 32);
        outDc = gdi32Pointer("CreateCompatibleDC", screen);
        PointerByReference pixels = new PointerByReference();
        outBitmap = gdi32Pointer("CreateDIBSection", outDc, info, 0, pixels, null, 0);
        outPixels = pixels.getValue();
        gdi32Pointer("SelectObject", outDc, outBitmap);
        gdi32("SetStretchBltMode", outDc, HALFTONE);
        gdi32("SetBrushOrgEx", outDc, 0, 0, null);
        outWidth = w;
        outHeight = h;
    }

    private static void free(Pointer dc, Pointer bitmap) {
        if (dc != null) gdi32("DeleteDC", dc);
        if (bitmap != null) gdi32("DeleteObject", bitmap);
    }
}
