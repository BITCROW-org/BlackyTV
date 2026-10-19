package org.bitcrow.blackytv.control;

import org.bitcrow.blackytv.capture.CaptureSource;
import org.bitcrow.blackytv.capture.ScreenCapture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

public final class TapControl {

    private static final int HOTBAR = 9;

    private static int lastSlot = -1;

    private TapControl() {
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        int slot = mc.player.getInventory().getSelectedSlot();
        int previous = lastSlot;
        lastSlot = slot;

        CaptureSource source = ScreenCapture.source();
        if (ControlState.isActive() || source == null || !source.isWindow()) return;
        TvRaycast.Aim aim = TvRaycast.fromCrosshair(mc);
        if (aim == null || blockInFront(mc, aim.distance())) return;

        WindowInput input = new WindowInput(source.hwnd());
        WindowInput.Target target = input.locate(aim.pointer().u(), aim.pointer().v());
        if (target == null) return;

        click(input, target, mc.options.keyAttack, WindowInput.LEFT);
        click(input, target, mc.options.keyUse, WindowInput.RIGHT);
        click(input, target, mc.options.keyPickItem, WindowInput.MIDDLE);

        int scrolled = scrollSteps(previous, slot);
        if (scrolled != 0 && !hotbarKeyDown(mc)) {
            mc.player.getInventory().setSelectedSlot(previous);
            lastSlot = previous;
            input.scroll(target, -scrolled, 0);
        }
    }

    private static void click(WindowInput input, WindowInput.Target target, KeyMapping key, int button) {
        while (key.consumeClick()) {
            input.press(target, button, 0);
            input.release(target, button, 0);
        }
        key.setDown(false);
    }

    static int scrollSteps(int previous, int now) {
        if (previous < 0 || previous == now) return 0;
        return Math.floorMod(now - previous + HOTBAR / 2, HOTBAR) - HOTBAR / 2;
    }

    private static boolean blockInFront(Minecraft mc, double tvDistance) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return false;
        return hit.getLocation().distanceTo(mc.player.getEyePosition(1f)) < tvDistance - 0.05;
    }

    private static boolean hotbarKeyDown(Minecraft mc) {
        for (KeyMapping key : mc.options.keyHotbarSlots) if (key.isDown()) return true;
        return false;
    }
}
