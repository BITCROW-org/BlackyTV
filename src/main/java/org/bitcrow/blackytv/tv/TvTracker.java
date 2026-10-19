package org.bitcrow.blackytv.tv;

import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.config.Settings;
import org.bitcrow.blackytv.stream.StreamReceiver;
import org.bitcrow.blackytv.stream.StreamSender;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class TvTracker {

    private static final int SCAN_RADIUS_CHUNKS = 6;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double FULL_VOLUME_DISTANCE = 4;
    private static final double SILENT_DISTANCE = 32;
    private static final double REFERENCE_WIDTH = 16;
    private static final double MAX_RANGE_SCALE = 3;

    private static final float DEFAULT_ASPECT = 16 / 9f;

    private static volatile List<Tv> tvs = List.of();
    private static volatile List<Tv.Speaker> speakers = List.of();
    private static int ticks;

    private TvTracker() {
    }

    public static List<Tv> all() {
        return tvs;
    }

    public static void tick(Minecraft mc) {
        if (ticks++ % SCAN_INTERVAL_TICKS == 0) scan(mc);
        updateVolumes(mc);
    }

    private static void scan(Minecraft mc) {
        List<Tv> found = new ArrayList<>();
        List<Tv.Speaker> foundSpeakers = new ArrayList<>();
        if (mc.level != null && mc.player != null) {
            int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
            for (int x = cx - SCAN_RADIUS_CHUNKS; x <= cx + SCAN_RADIUS_CHUNKS; x++) {
                for (int z = cz - SCAN_RADIUS_CHUNKS; z <= cz + SCAN_RADIUS_CHUNKS; z++) {
                    if (!mc.level.hasChunk(x, z)) continue;
                    for (BlockEntity be : mc.level.getChunk(x, z).getBlockEntities().values()) {
                        if (!(be instanceof SignBlockEntity sign)) continue;
                        Tv tv = Tv.fromSign(sign);
                        if (tv != null) found.add(tv);
                        Tv.Speaker speaker = Tv.speakerFromSign(sign);
                        if (speaker != null) foundSpeakers.add(speaker);
                    }
                }
            }
        }
        tvs = found;
        speakers = foundSpeakers;

        Set<String> remote = new HashSet<>();
        for (Tv tv : found) if (!tv.isLocal()) remote.add(tv.feed());
        for (Tv.Speaker speaker : foundSpeakers) remote.add(speaker.feed());
        ScreenCapture.setWanted(found.stream().anyMatch(Tv::isLocal));
        StreamReceiver.watch(remote);
    }

    private static void updateVolumes(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 player = mc.player.getEyePosition(1f);
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER) * Settings.volume / 100f;

        Map<String, Float> loudest = new HashMap<>();
        for (Tv tv : tvs) {
            if (tv.isLocal()) continue;
            float aspect = TvRenderer.aspect(tv.feed());
            TvGeometry geometry = TvGeometry.of(tv, aspect > 0 ? aspect : DEFAULT_ASPECT);
            double distance = geometry.distanceTo(player.x, player.y, player.z);
            loudest.merge(tv.feed(), falloff(distance, rangeScale(geometry.width())), Math::max);
        }
        for (Tv.Speaker speaker : speakers)
            loudest.merge(speaker.feed(), falloff(Vec3.atCenterOf(speaker.pos()).distanceTo(player), 1), Math::max);

        String own = StreamSender.isSending() ? StreamSender.ownFeed() : null;
        loudest.forEach((feed, level) -> StreamReceiver.setVolume(feed, feed.equals(own) ? 0 : level * master));
    }

    static double rangeScale(double canvasWidth) {
        return Math.max(1, Math.min(MAX_RANGE_SCALE, canvasWidth / REFERENCE_WIDTH));
    }

    static float falloff(double distance, double scale) {
        double full = FULL_VOLUME_DISTANCE * scale, silent = SILENT_DISTANCE * scale;
        return (float) Math.max(0, Math.min(1, 1 - (distance - full) / (silent - full)));
    }
}
