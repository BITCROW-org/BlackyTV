package org.bitcrow.blackytv.capture;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

final class Win32 {

    static final int GWL_EXSTYLE = -20;
    static final int SRCCOPY = 0x00CC0020;
    static final int SWP_NO_SIZE_MOVE_ACTIVATE = 0x13;
    static final Pointer HWND_TOP_MOST = Pointer.createConstant(-1);
    static final Pointer HWND_NOT_TOP_MOST = Pointer.createConstant(-2);
    static final Pointer HWND_BOTTOM = Pointer.createConstant(1);

    private Win32() {
    }

    static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    static Function function(String library, String name) {
        return NativeLibrary.getInstance(library).getFunction(name);
    }

    static int user32(String name, Object... args) {
        return function("user32", name).invokeInt(args);
    }

    static long user32Long(String name, Object... args) {
        return function("user32", name).invokeLong(args);
    }

    static Pointer user32Pointer(String name, Object... args) {
        return function("user32", name).invokePointer(args);
    }

    static int gdi32(String name, Object... args) {
        return function("gdi32", name).invokeInt(args);
    }

    static Pointer gdi32Pointer(String name, Object... args) {
        return function("gdi32", name).invokePointer(args);
    }
}
