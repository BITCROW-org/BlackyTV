package org.bitcrow.blackytv.tv;

public record TvGeometry(double centerX, double centerZ, double top, double videoTop,
                         double frontX, double frontZ, double rightX, double rightZ,
                         double width, double height, double videoWidth, double videoHeight) {

    public static final double DEPTH_BEHIND_SIGN = 0.55;

    public record Hit(double u, double v, double distance, double x, double y, double z) {
    }

    public static TvGeometry of(double signX, double signY, double signZ, float yaw,
                                float boardWidth, float boardHeight,
                                double offRight, double offUp, double offForward, float aspect) {
        double radians = Math.toRadians(yaw);
        double frontX = -Math.sin(radians), frontZ = Math.cos(radians);
        double rightX = -frontZ, rightZ = frontX;

        double width = boardWidth, height = boardHeight;
        if (width <= 0) width = height * aspect;
        if (height <= 0) height = width / aspect;

        double centerX = signX + 0.5 - frontX * (DEPTH_BEHIND_SIGN + offForward) + rightX * offRight;
        double centerZ = signZ + 0.5 - frontZ * (DEPTH_BEHIND_SIGN + offForward) + rightZ * offRight;
        double top = signY + offUp;

        double videoWidth = width, videoHeight = width / aspect;
        if (videoHeight > height) {
            videoHeight = height;
            videoWidth = height * aspect;
        }
        double videoTop = top - (height - videoHeight) / 2;

        return new TvGeometry(centerX, centerZ, top, videoTop, frontX, frontZ, rightX, rightZ, width, height, videoWidth, videoHeight);
    }

    public static TvGeometry of(Tv tv, float aspect) {
        return of(tv.pos().getX(), tv.pos().getY(), tv.pos().getZ(), tv.yaw(), tv.width(), tv.height(),
                tv.offset().x, tv.offset().y, tv.offset().z, aspect);
    }

    public Hit raycast(double ox, double oy, double oz, double dx, double dy, double dz) {
        double facing = dx * frontX + dz * frontZ;
        if (Math.abs(facing) < 1e-6) return null;
        double t = ((centerX - ox) * frontX + (centerZ - oz) * frontZ) / facing;
        if (t <= 0) return null;

        double hx = ox + dx * t, hy = oy + dy * t, hz = oz + dz * t;
        double along = (hx - centerX) * rightX + (hz - centerZ) * rightZ;
        double u = (along + videoWidth / 2) / videoWidth;
        double v = (videoTop - hy) / videoHeight;
        if (u < 0 || u > 1 || v < 0 || v > 1) return null;

        double distance = t * Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new Hit(u, v, distance, hx, hy, hz);
    }

    public double[] pointAt(double u, double v) {
        double along = u * videoWidth - videoWidth / 2;
        return new double[]{centerX + rightX * along, videoTop - v * videoHeight, centerZ + rightZ * along};
    }
}
