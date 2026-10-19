package org.bitcrow.blackytv.control;

import org.bitcrow.blackytv.stream.StreamSender;
import org.bitcrow.blackytv.tv.Tv;
import org.bitcrow.blackytv.tv.TvGeometry;
import org.bitcrow.blackytv.tv.TvRenderer;
import org.bitcrow.blackytv.tv.TvTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class TvRaycast {

    private TvRaycast() {
    }

    static boolean showsOwnSource(Tv tv) {
        return tv.isLocal() || (StreamSender.isSending() && tv.feed().equals(StreamSender.ownFeed()));
    }

    record Aim(ControlState.Pointer pointer, double distance) {
    }

    static ControlState.Pointer fromCursor(Minecraft mc, double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Aim aim = aim(mc, mouseX, mouseY, screenWidth, screenHeight);
        return aim == null ? null : aim.pointer();
    }

    static Aim fromCrosshair(Minecraft mc) {
        return aim(mc, 1, 1, 2, 2);
    }

    private static Aim aim(Minecraft mc, double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (mc.player == null) return null;
        double[] ray = cursorRay(mc.player.getYRot(), mc.player.getXRot(), mc.options.fov().get(),
                mouseX, mouseY, screenWidth, screenHeight);
        Vec3 eye = mc.player.getEyePosition(1f);

        Aim best = null;
        for (Tv tv : TvTracker.all()) {
            if (!showsOwnSource(tv)) continue;
            float aspect = TvRenderer.aspect(tv.feed());
            if (aspect <= 0) continue;
            TvGeometry.Hit hit = TvGeometry.of(tv, aspect).raycast(eye.x, eye.y, eye.z, ray[0], ray[1], ray[2]);
            if (hit != null && (best == null || hit.distance() < best.distance()))
                best = new Aim(new ControlState.Pointer(tv, hit.u(), hit.v()), hit.distance());
        }
        return best;
    }

    static double[] cursorRay(float yawDegrees, float pitchDegrees, double fovDegrees,
                              double mouseX, double mouseY, int screenWidth, int screenHeight) {
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        double tanHalf = Math.tan(Math.toRadians(fovDegrees) / 2);
        double aspect = screenWidth / (double) screenHeight;
        double nx = (2 * mouseX / screenWidth - 1) * tanHalf * aspect;
        double ny = (1 - 2 * mouseY / screenHeight) * tanHalf;
        return new double[]{fx + rx * nx + ux * ny, fy + uy * ny, fz + rz * nx + uz * ny};
    }
}
