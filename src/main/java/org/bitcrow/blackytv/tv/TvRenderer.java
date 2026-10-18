package org.bitcrow.blackytv.tv;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.bitcrow.blackytv.BlackyTV;
import org.bitcrow.blackytv.capture.Frame;
import org.bitcrow.blackytv.capture.ScreenCapture;
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

    private static final double DEPTH_BEHIND_SIGN = 0.55;
    private static final double BACKGROUND_OFFSET = 0.01;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

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

    public static void render(LevelRenderContext context) {
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Tv tv : TvTracker.all()) {
            Screen screen = SCREENS.get(tv.feed());
            if (screen != null && screen.texture != null) render(context, camera, tv, screen);
        }
    }

    private static void render(LevelRenderContext context, Vec3 camera, Tv tv, Screen screen) {
        float aspect = screen.aspect;
        boolean hasVideo = screen.shown != null;

        double yaw = Math.toRadians(tv.yaw());
        double frontX = -Math.sin(yaw), frontZ = Math.cos(yaw);
        double rightX = -frontZ, rightZ = frontX;

        float width = tv.width(), height = tv.height();
        if (width <= 0) width = height * aspect;
        if (height <= 0) height = width / aspect;

        Vec3 o = tv.offset();
        double centerX = tv.pos().getX() + 0.5 - frontX * (DEPTH_BEHIND_SIGN + o.z) + rightX * o.x - camera.x;
        double centerZ = tv.pos().getZ() + 0.5 - frontZ * (DEPTH_BEHIND_SIGN + o.z) + rightZ * o.x - camera.z;
        double top = tv.pos().getY() + o.y - camera.y;

        float videoWidth = width, videoHeight = width / aspect;
        if (videoHeight > height) {
            videoHeight = height;
            videoWidth = height * aspect;
        }
        double videoTop = top - (height - videoHeight) / 2;

        Quad background = new Quad(centerX + frontX * BACKGROUND_OFFSET, top, centerZ + frontZ * BACKGROUND_OFFSET, rightX, rightZ, width, height, BLACK);
        Quad video = new Quad(centerX, videoTop, centerZ, rightX, rightZ, videoWidth, videoHeight, WHITE);

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.text(screen.id), (pose, buffer) -> {
            background.draw(pose, buffer);
            if (hasVideo) video.draw(pose, buffer);
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
