package org.bitcrow.blackytv.control;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.bitcrow.blackytv.capture.ClientArea;
import org.bitcrow.blackytv.capture.Win32;

final class WindowInput {

    static final int LEFT = 0, RIGHT = 1, MIDDLE = 2;

    private static final int WM_KEYDOWN = 0x100, WM_KEYUP = 0x101, WM_CHAR = 0x102;
    private static final int WM_MOUSEMOVE = 0x200, WM_MOUSEWHEEL = 0x20A;
    private static final int[] BUTTON_DOWN = {0x201, 0x204, 0x207};
    private static final int[] BUTTON_UP = {0x202, 0x205, 0x208};
    private static final int[] BUTTON_FLAG = {0x1, 0x2, 0x10};
    private static final int MK_SHIFT = 0x4, MK_CONTROL = 0x8;
    private static final int CWP_SKIP = 0x1 | 0x2 | 0x4;
    private static final int MAPVK_VK_TO_VSC = 0;
    private static final int WHEEL_DELTA = 120;

    record Target(Pointer hwnd, int clientX, int clientY, int screenX, int screenY) {
    }

    private final Pointer window;
    private Pointer lastClicked;
    private int heldButtons;

    WindowInput(Pointer window) {
        this.window = window;
    }

    boolean isAlive() {
        return Win32.user32("IsWindow", window) != 0;
    }

    Target locate(double u, double v) {
        ClientArea area = ClientArea.of(window);
        if (area == null) return null;
        int screenX = area.screenX() + (int) Math.min(area.width() - 1, Math.floor(u * area.width()));
        int screenY = area.screenY() + (int) Math.min(area.height() - 1, Math.floor(v * area.height()));

        Pointer target = window;
        Memory point = new Memory(8);
        while (true) {
            point.setInt(0, screenX);
            point.setInt(4, screenY);
            Win32.user32("ScreenToClient", target, point);
            long packed = ((long) point.getInt(4) << 32) | (point.getInt(0) & 0xFFFFFFFFL);
            Pointer child = Win32.user32Pointer("ChildWindowFromPointEx", target, packed, CWP_SKIP);
            if (child == null || child.equals(target)) break;
            target = child;
        }
        point.setInt(0, screenX);
        point.setInt(4, screenY);
        Win32.user32("ScreenToClient", target, point);
        return new Target(target, point.getInt(0), point.getInt(4), screenX, screenY);
    }

    boolean move(Target t, int modifiers) {
        return post(t.hwnd(), WM_MOUSEMOVE, heldButtons | mouseModifiers(modifiers), coords(t.clientX(), t.clientY()));
    }

    boolean press(Target t, int button, int modifiers) {
        lastClicked = t.hwnd();
        heldButtons |= BUTTON_FLAG[button];
        move(t, modifiers);
        return post(t.hwnd(), BUTTON_DOWN[button], heldButtons | mouseModifiers(modifiers), coords(t.clientX(), t.clientY()));
    }

    boolean release(Target t, int button, int modifiers) {
        heldButtons &= ~BUTTON_FLAG[button];
        return post(t.hwnd(), BUTTON_UP[button], heldButtons | mouseModifiers(modifiers), coords(t.clientX(), t.clientY()));
    }

    void releaseAll(Target t) {
        for (int button = LEFT; button <= MIDDLE; button++)
            if ((heldButtons & BUTTON_FLAG[button]) != 0 && t != null) release(t, button, 0);
        heldButtons = 0;
    }

    boolean scroll(Target t, double amount, int modifiers) {
        int delta = (int) Math.round(amount * WHEEL_DELTA);
        if (delta == 0) return true;
        long wParam = ((long) (delta & 0xFFFF) << 16) | heldButtons | mouseModifiers(modifiers);
        return post(t.hwnd(), WM_MOUSEWHEEL, wParam, coords(t.screenX(), t.screenY()));
    }

    boolean type(int codepoint) {
        Pointer focus = keyboardTarget();
        boolean ok = true;
        for (char c : Character.toChars(codepoint)) ok &= post(focus, WM_CHAR, c, 1);
        return ok;
    }

    boolean keyDown(KeyCodes.Key key) {
        return post(keyboardTarget(), WM_KEYDOWN, key.vk(), keyParam(key, false));
    }

    boolean keyUp(KeyCodes.Key key) {
        return post(keyboardTarget(), WM_KEYUP, key.vk(), keyParam(key, true));
    }

    boolean shortcut(KeyCodes.Key key, int modifiers) {
        KeyCodes.Key ctrl = new KeyCodes.Key(KeyCodes.VK_CONTROL, false);
        KeyCodes.Key shift = new KeyCodes.Key(KeyCodes.VK_SHIFT, false);
        KeyCodes.Key alt = new KeyCodes.Key(KeyCodes.VK_MENU, false);
        boolean withCtrl = KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_CONTROL);
        boolean withShift = KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_SHIFT);
        boolean withAlt = KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_ALT);
        if (withCtrl) keyDown(ctrl);
        if (withShift) keyDown(shift);
        if (withAlt) keyDown(alt);
        boolean ok = keyDown(key) && keyUp(key);
        if (withAlt) keyUp(alt);
        if (withShift) keyUp(shift);
        if (withCtrl) keyUp(ctrl);
        return ok;
    }

    private Pointer keyboardTarget() {
        int thread = Win32.user32("GetWindowThreadProcessId", window, null);
        Memory info = new Memory(72);
        info.clear();
        info.setInt(0, 72);
        if (thread != 0 && Win32.user32("GetGUIThreadInfo", thread, info) != 0) {
            Pointer focus = info.getPointer(16);
            if (focus != null && (focus.equals(window) || Win32.user32("IsChild", window, focus) != 0)) return focus;
        }
        return lastClicked != null && Win32.user32("IsWindow", lastClicked) != 0 ? lastClicked : window;
    }

    private static long keyParam(KeyCodes.Key key, boolean up) {
        long scan = Win32.user32("MapVirtualKeyW", key.vk(), MAPVK_VK_TO_VSC) & 0xFF;
        long param = 1 | (scan << 16) | (key.extended() ? 1L << 24 : 0);
        if (up) param |= (1L << 30) | (1L << 31);
        return param;
    }

    private static int mouseModifiers(int modifiers) {
        int flags = 0;
        if (KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_SHIFT)) flags |= MK_SHIFT;
        if (KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_CONTROL)) flags |= MK_CONTROL;
        return flags;
    }

    private static long coords(int x, int y) {
        return ((long) (y & 0xFFFF) << 16) | (x & 0xFFFF);
    }

    private static boolean post(Pointer hwnd, int message, long wParam, long lParam) {
        return Win32.user32("PostMessageW", hwnd, message, wParam, lParam) != 0;
    }
}
