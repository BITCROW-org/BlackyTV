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

    private static volatile List<Tv> tvs = List.of();
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
        if (mc.level != null && mc.player != null) {
            int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
            for (int x = cx - SCAN_RADIUS_CHUNKS; x <= cx + SCAN_RADIUS_CHUNKS; x++) {
                for (int z = cz - SCAN_RADIUS_CHUNKS; z <= cz + SCAN_RADIUS_CHUNKS; z++) {
                    if (!mc.level.hasChunk(x, z)) continue;
                    for (BlockEntity be : mc.level.getChunk(x, z).getBlockEntities().values()) {
                        if (!(be instanceof SignBlockEntity sign)) continue;
                        Tv tv = Tv.fromSign(sign);
                        if (tv != null) found.add(tv);
                    }
                }
            }
        }
        tvs = found;

        Set<String> remote = new HashSet<>();
        for (Tv tv : found) if (!tv.isLocal()) remote.add(tv.feed());
        ScreenCapture.setWanted(found.stream().anyMatch(Tv::isLocal));
        StreamReceiver.watch(remote);
    }

    private static void updateVolumes(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 player = mc.player.position();
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER) * Settings.volume / 100f;

        Map<String, Double> nearest = new HashMap<>();
        for (Tv tv : tvs)
            if (!tv.isLocal()) nearest.merge(tv.feed(), Vec3.atCenterOf(tv.pos()).distanceTo(player), Math::min);

        String own = StreamSender.isSending() ? StreamSender.ownFeed() : null;
        nearest.forEach((feed, distance) -> {
            float falloff = (float) (1 - (distance - FULL_VOLUME_DISTANCE) / (SILENT_DISTANCE - FULL_VOLUME_DISTANCE));
            float volume = feed.equals(own) ? 0 : Math.max(0, Math.min(1, falloff)) * master;
            StreamReceiver.setVolume(feed, volume);
        });
    }
}
