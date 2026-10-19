package org.bitcrow.blackytv.control;

import java.util.Map;

final class KeyCodes {

    static final int GLFW_MOD_SHIFT = 0x1;
    static final int GLFW_MOD_CONTROL = 0x2;
    static final int GLFW_MOD_ALT = 0x4;
    static final int GLFW_KEY_ESCAPE = 256;

    static final int VK_SHIFT = 0x10;
    static final int VK_CONTROL = 0x11;
    static final int VK_MENU = 0x12;
    static final int VK_ESCAPE = 0x1B;

    record Key(int vk, boolean extended) {
    }

    private static final Map<Integer, Key> SPECIAL = Map.ofEntries(
            Map.entry(256, new Key(0x1B, false)),
            Map.entry(257, new Key(0x0D, false)),
            Map.entry(258, new Key(0x09, false)),
            Map.entry(259, new Key(0x08, false)),
            Map.entry(260, new Key(0x2D, true)),
            Map.entry(261, new Key(0x2E, true)),
            Map.entry(262, new Key(0x27, true)),
            Map.entry(263, new Key(0x25, true)),
            Map.entry(264, new Key(0x28, true)),
            Map.entry(265, new Key(0x26, true)),
            Map.entry(266, new Key(0x21, true)),
            Map.entry(267, new Key(0x22, true)),
            Map.entry(268, new Key(0x24, true)),
            Map.entry(269, new Key(0x23, true)),
            Map.entry(335, new Key(0x0D, true))
    );

    private KeyCodes() {
    }

    static Key special(int glfwKey) {
        if (glfwKey >= 290 && glfwKey <= 301) return new Key(0x70 + glfwKey - 290, false);
        return SPECIAL.get(glfwKey);
    }

    static Key shortcutKey(int glfwKey) {
        if (glfwKey >= 65 && glfwKey <= 90) return new Key(glfwKey, false);
        if (glfwKey >= 48 && glfwKey <= 57) return new Key(glfwKey, false);
        return null;
    }

    static boolean has(int modifiers, int flag) {
        return (modifiers & flag) != 0;
    }
}
