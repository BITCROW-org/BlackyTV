package org.bitcrow.blackytv.control;

import org.bitcrow.blackytv.capture.CaptureSource;
import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.command.BlackyTvCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ControlScreen extends Screen {

    private static final long MOVE_INTERVAL_MS = 16;

    private final CaptureSource source;
    private final WindowInput input;
    private WindowInput.Target lastTarget;
    private long lastMove;
    private boolean warnedBlocked;

    private ControlScreen(CaptureSource source) {
        super(Component.translatable("blackytv.control.title"));
        this.source = source;
        this.input = new WindowInput(source.hwnd());
    }

    public static void open(Minecraft mc) {
        CaptureSource source = ScreenCapture.source();
        if (!ScreenCapture.isSupported()) {
            tell(mc, "blackytv.control.not_supported");
        } else if (source == null || !source.isWindow()) {
            tell(mc, "blackytv.control.no_window");
        } else {
            ControlState.start();
            mc.setScreenAndShow(new ControlScreen(source));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (ScreenCapture.source() != source || !input.isAlive()) {
            tell(minecraft, "blackytv.control.closed");
            onClose();
        }
    }

    @Override
    public void removed() {
        input.releaseAll(lastTarget);
        ControlState.stop();
        super.removed();
    }

    private WindowInput.Target aim(double mouseX, double mouseY) {
        ControlState.Pointer pointer = TvRaycast.fromCursor(minecraft, mouseX, mouseY, width, height);
        ControlState.point(pointer);
        if (pointer == null) return null;
        WindowInput.Target target = input.locate(pointer.u(), pointer.v());
        if (target != null) lastTarget = target;
        return target;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        if (now - lastMove < MOVE_INTERVAL_MS) return;
        lastMove = now;
        WindowInput.Target target = aim(mouseX, mouseY);
        if (target != null) check(input.move(target, 0));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WindowInput.Target target = aim(event.x(), event.y());
        if (target != null && event.button() <= WindowInput.MIDDLE) check(input.press(target, event.button(), 0));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        WindowInput.Target target = aim(event.x(), event.y());
        if (target == null) target = lastTarget;
        if (target != null && event.button() <= WindowInput.MIDDLE) check(input.release(target, event.button(), 0));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        mouseMoved(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        WindowInput.Target target = aim(mouseX, mouseY);
        if (target != null) check(input.scroll(target, scrollY, 0));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key(), modifiers = event.modifiers();
        boolean shift = KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_SHIFT);
        if (key == KeyCodes.GLFW_KEY_ESCAPE && !shift) {
            onClose();
            return true;
        }

        KeyCodes.Key special = KeyCodes.special(key);
        if (special != null) {
            check(input.keyDown(special));
            return true;
        }
        boolean shortcut = KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_CONTROL) || KeyCodes.has(modifiers, KeyCodes.GLFW_MOD_ALT);
        KeyCodes.Key letter = KeyCodes.shortcutKey(key);
        if (shortcut && letter != null) check(input.shortcut(letter, modifiers));
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyCodes.Key special = KeyCodes.special(event.key());
        if (special != null) check(input.keyUp(special));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        check(input.type(event.codepoint()));
        return true;
    }

    private void check(boolean delivered) {
        if (delivered || warnedBlocked) return;
        warnedBlocked = true;
        tell(minecraft, "blackytv.control.blocked");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Component hint = Component.translatable("blackytv.control.hint").withStyle(ChatFormatting.GRAY);
        g.text(font, hint, (width - font.width(hint)) / 2, height - 14, 0xFFFFFFFF, true);
        if (ControlState.pointer() == null) {
            Component aimHint = Component.translatable("blackytv.control.no_tv").withStyle(ChatFormatting.YELLOW);
            g.text(font, aimHint, (width - font.width(aimHint)) / 2, height - 28, 0xFFFFFFFF, true);
        }
    }

    private static void tell(Minecraft mc, String key) {
        if (mc.player != null) mc.player.sendSystemMessage(BlackyTvCommand.message(Component.translatable(key)));
    }
}
