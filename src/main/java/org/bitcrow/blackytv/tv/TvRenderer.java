package org.bitcrow.blackytv.tv;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.bitcrow.blackytv.BlackyTV;
import org.bitcrow.blackytv.capture.Frame;
import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.control.ControlState;
import org.bitcrow.blackytv.stream.StreamReceiver;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.util.HashMap;
import java.util.Map;

public final class TvRenderer {

    private static final double BACKGROUND_OFFSET = 0.01;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int POINTER = 0xFFFF3B3B;
    private static final double POINTER_LIFT = 0.03;

    private static final class Screen {
        final Identifier id;
        DynamicTexture texture;
        Frame shown;
        float aspect = 16 / 9f;

        Screen(String feed) {
            id = BlackyTV.id("screen/" + Integer.toHexString(feed.hashCode()));
        }
    }

    private static final Map<String, Screen> SCREENS = new HashMap<>();

    private TvRenderer() {
    }

    public static void upload() {
        for (Tv tv : TvTracker.all()) {
            Screen screen = SCREENS.computeIfAbsent(tv.feed(), Screen::new);
            if (screen.texture == null) screen.texture = createTexture(screen, 1, 1);

            Frame frame = tv.isLocal() ? ScreenCapture.frame() : StreamReceiver.frame(tv.feed());
            if (frame == null) {
                screen.shown = null;
                continue;
            }
            if (frame == screen.shown) continue;

            NativeImage image = screen.texture.getPixels();
            if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
                screen.texture = createTexture(screen, frame.width(), frame.height());
                image = screen.texture.getPixels();
            }
            MemoryUtil.memIntBuffer(image.getPointer(), frame.width() * frame.height()).put(frame.abgr());
            screen.texture.upload();
            screen.shown = frame;
            screen.aspect = frame.aspect();
        }
    }

    private static DynamicTexture createTexture(Screen screen, int width, int height) {
        DynamicTexture texture = new DynamicTexture(() -> "blackytv " + screen.id, new NativeImage(width, height, true));
        Minecraft.getInstance().getTextureManager().register(screen.id, texture);
        return texture;
    }

    public static float aspect(String feed) {
        Screen screen = SCREENS.get(feed);
        return screen == null || screen.shown == null ? 0 : screen.aspect;
    }

    public static void render(LevelRenderContext context) {
        Vec3 camera = context.levelState().cameraRenderState.pos;
        ControlState.Pointer pointer = ControlState.pointer();
        for (Tv tv : TvTracker.all()) {
            Screen screen = SCREENS.get(tv.feed());
            if (screen == null || screen.texture == null) continue;
            TvGeometry geometry = TvGeometry.of(tv, screen.aspect);
            render(context, camera, geometry, screen);
            if (pointer != null && pointer.tv().equals(tv) && screen.shown != null)
                renderPointer(context, camera, geometry, pointer);
        }
    }

    private static void render(LevelRenderContext context, Vec3 camera, TvGeometry g, Screen screen) {
        boolean hasVideo = screen.shown != null;
        double cx = g.centerX() - camera.x, cz = g.centerZ() - camera.z;
        double top = g.top() - camera.y, videoTop = g.videoTop() - camera.y;

        Quad background = new Quad(cx + g.frontX() * BACKGROUND_OFFSET, top, cz + g.frontZ() * BACKGROUND_OFFSET,
                g.rightX(), g.rightZ(), (float) g.width(), (float) g.height(), BLACK);
        Quad video = new Quad(cx, videoTop, cz, g.rightX(), g.rightZ(), (float) g.videoWidth(), (float) g.videoHeight(), WHITE);

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.text(screen.id), (pose, buffer) -> {
            background.draw(pose, buffer);
            if (hasVideo) video.draw(pose, buffer);
        });
    }

    private static void renderPointer(LevelRenderContext context, Vec3 camera, TvGeometry g, ControlState.Pointer pointer) {
        double[] p = g.pointAt(pointer.u(), pointer.v());
        double side = Math.signum((camera.x - g.centerX()) * g.frontX() + (camera.z - g.centerZ()) * g.frontZ());
        double size = Math.max(0.04, g.videoWidth() / 120);
        double x = p[0] - camera.x + g.frontX() * side * POINTER_LIFT, y = p[1] - camera.y, z = p[2] - camera.z + g.frontZ() * side * POINTER_LIFT;
        float l = (float) size;
        float x0 = (float) (x - g.rightX() * l), z0 = (float) (z - g.rightZ() * l);
        float x1 = (float) (x + g.rightX() * l), z1 = (float) (z + g.rightZ() * l);
        float y0 = (float) (y - l), y1 = (float) (y + l);

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugFilledBox(), (pose, buffer) -> {
            buffer.addVertex(pose, x0, y1, z0).setColor(POINTER);
            buffer.addVertex(pose, x0, y0, z0).setColor(POINTER);
            buffer.addVertex(pose, x1, y0, z1).setColor(POINTER);
            buffer.addVertex(pose, x1, y1, z1).setColor(POINTER);
            buffer.addVertex(pose, x1, y1, z1).setColor(POINTER);
            buffer.addVertex(pose, x1, y0, z1).setColor(POINTER);
            buffer.addVertex(pose, x0, y0, z0).setColor(POINTER);
            buffer.addVertex(pose, x0, y1, z0).setColor(POINTER);
        });
    }

    private record Quad(double centerX, double top, double centerZ, double rightX, double rightZ, float width, float height, int color) {

        void draw(PoseStack.Pose pose, VertexConsumer buffer) {
            float leftX = (float) (centerX - rightX * width / 2), leftZ = (float) (centerZ - rightZ * width / 2);
            float rightEdgeX = (float) (centerX + rightX * width / 2), rightEdgeZ = (float) (centerZ + rightZ * width / 2);
            float upper = (float) top, lower = (float) (top - height);

            vertex(pose, buffer, leftX, upper, leftZ, 0, 0);
            vertex(pose, buffer, leftX, lower, leftZ, 0, 1);
            vertex(pose, buffer, rightEdgeX, lower, rightEdgeZ, 1, 1);
            vertex(pose, buffer, rightEdgeX, upper, rightEdgeZ, 1, 0);

            vertex(pose, buffer, rightEdgeX, upper, rightEdgeZ, 1, 0);
            vertex(pose, buffer, rightEdgeX, lower, rightEdgeZ, 1, 1);
            vertex(pose, buffer, leftX, lower, leftZ, 0, 1);
            vertex(pose, buffer, leftX, upper, leftZ, 0, 0);
        }

        private void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v) {
            buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT);
        }
    }
}
