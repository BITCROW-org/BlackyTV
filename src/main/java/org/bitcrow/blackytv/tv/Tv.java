package org.bitcrow.blackytv.tv;

import org.bitcrow.blackytv.stream.RelayAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;

public record Tv(BlockPos pos, float yaw, float width, float height, Vec3 offset, String feed) {

    public static final String SIGN_ID = "blackytv:screen";
    public static final String LOCAL_FEED = "";
    public static final float MAX_WIDTH = 100;
    public static final float MAX_HEIGHT = 56;

    public boolean isLocal() {
        return feed.isEmpty();
    }

    public static Tv fromSign(SignBlockEntity sign) {
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) lines[i] = sign.getFrontText().getMessage(i, false).getString().trim();
        if (!lines[0].equalsIgnoreCase(SIGN_ID)) return null;

        float width = 16, height = 9;
        String[] size = lines[1].toLowerCase().split("x");
        try {
            width = Math.min(MAX_WIDTH, Float.parseFloat(size[0]));
            height = Math.min(MAX_HEIGHT, Float.parseFloat(size[1]));
        } catch (RuntimeException ignored) {
        }
        if (width <= 0 && height <= 0) {
            width = 16;
            height = 9;
        }

        Vec3 offset = Vec3.ZERO;
        String channel = null, host = null;
        for (String arg : (lines[2] + " " + lines[3]).split("\\s+")) {
            if (arg.startsWith("off:")) offset = parseOffset(arg.substring(4), offset);
            else if (arg.startsWith("ch:") && arg.length() > 3) channel = arg.substring(3).toLowerCase();
            else if (arg.startsWith("@") && arg.length() > 1) host = arg.substring(1);
        }
        String feed = channel == null ? LOCAL_FEED : RelayAddress.feed(RelayAddress.resolve(host), channel);

        float yaw = sign.getBlockState().getBlock() instanceof SignBlock block ? block.getYRotationDegrees(sign.getBlockState()) : 0;
        return new Tv(sign.getBlockPos(), yaw, width, height, offset, feed);
    }

    private static Vec3 parseOffset(String text, Vec3 fallback) {
        String[] parts = text.split(",");
        try {
            return new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
