package org.bitcrow.blackytv.gui;

import org.bitcrow.blackytv.capture.CaptureSource;
import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.config.Settings;
import org.bitcrow.blackytv.control.ControlScreen;
import org.bitcrow.blackytv.stream.StreamSender;
import org.bitcrow.blackytv.tv.TvTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class TvScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int ROW = 22;
    private static final int TOP = 36;
    private static final int SMALL = 80;
    private static final int[] FPS_STEPS = {5, 10, 20, 30, 60};
    private static final int[] QUALITY_STEPS = {640, 960, 1280, 1920};
    private static final int[] VOLUME_STEPS = {0, 25, 50, 75, 100, 150, 200};

    private List<CaptureSource> sources;
    private int page, perPage;

    public TvScreen() {
        super(Component.literal("BlackyTV"));
    }

    @Override
    protected void init() {
        if (sources == null) sources = ScreenCapture.list();
        perPage = Math.max(3, (height - TOP - 156) / ROW);
        int pages = Math.max(1, (sources.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int x = (width - WIDTH) / 2;

        addSourceButtons(x);

        int y = TOP + ROW * (perPage + 1) + 6;
        addRenderableWidget(button(Component.literal("<"), () -> page--, x, y, 20)).active = page > 0;
        addRenderableWidget(button(Component.literal(">"), () -> page++, x + 24, y, 20)).active = page < pages - 1;
        addRenderableWidget(cycle(tr("menu.fps", Settings.fps), FPS_STEPS, () -> Settings.fps, v -> Settings.fps = v, x + 48, y, 70));
        addRenderableWidget(button(tr("menu.reload"), () -> sources = null, x + 122, y, 76));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x + WIDTH - 78, y, 78, 20).build());

        y += 24;
        addRenderableWidget(button(sendLabel(), this::toggleSending, x, y, WIDTH - SMALL - 4)).active = StreamSender.isConfigured();
        addRenderableWidget(cycle(tr("menu.stream_volume", Settings.streamVolume + "%"), VOLUME_STEPS,
                () -> Settings.streamVolume, v -> Settings.streamVolume = v, x + WIDTH - SMALL, y, SMALL));

        y += 24;
        Component awake = Settings.keepAwake ? tr("menu.keep_awake.on").withStyle(ChatFormatting.GREEN) : tr("menu.keep_awake.off");
        addRenderableWidget(button(awake, () -> Settings.keepAwake = !Settings.keepAwake, x, y, WIDTH - 2 * SMALL - 8));
        addRenderableWidget(cycle(tr("menu.volume", Settings.volume + "%"), VOLUME_STEPS,
                () -> Settings.volume, v -> Settings.volume = v, x + WIDTH - 2 * SMALL - 4, y, SMALL));
        addRenderableWidget(cycle(tr("menu.quality", Settings.quality), QUALITY_STEPS,
                () -> Settings.quality, v -> Settings.quality = v, x + WIDTH - SMALL, y, SMALL));

        y += 24;
        CaptureSource current = ScreenCapture.source();
        addRenderableWidget(Button.builder(tr("menu.control"), b -> ControlScreen.open(minecraft))
                .bounds(x, y, WIDTH / 2 - 2, 20).build()).active = current != null && current.isWindow();
        Component tap = Settings.tapControl ? tr("menu.tap.on").withStyle(ChatFormatting.GREEN) : tr("menu.tap.off");
        addRenderableWidget(button(tap, () -> Settings.tapControl = !Settings.tapControl, x + WIDTH / 2 + 2, y, WIDTH / 2 - 2));

        y += 24;
        Component cursor = Settings.showCursor ? tr("menu.cursor.on").withStyle(ChatFormatting.GREEN) : tr("menu.cursor.off");
        addRenderableWidget(button(cursor, () -> Settings.showCursor = !Settings.showCursor, x, y, WIDTH));
    }

    private void addSourceButtons(int x) {
        int y = TOP;
        CaptureSource current = ScreenCapture.source();
        addRenderableWidget(Button.builder(selectable(tr("menu.off"), current == null), b -> pick(null)).bounds(x, y, WIDTH, 20).build());
        for (int i = page * perPage; i < Math.min(sources.size(), (page + 1) * perPage); i++) {
            CaptureSource source = sources.get(i);
            y += ROW;
            addRenderableWidget(Button.builder(selectable(sourceName(source, i), source.sameAs(current)), b -> pick(source))
                    .bounds(x, y, WIDTH, 20).build());
        }
    }

    private Component sourceName(CaptureSource source, int index) {
        if (!source.isWindow()) return tr("menu.monitor", index + 1, source.width() + "x" + source.height());
        return Component.literal(font.plainSubstrByWidth(source.name(), WIDTH - 24));
    }

    private Component selectable(Component text, boolean selected) {
        if (!selected) return text;
        return Component.literal("▶ ").append(text).withStyle(ChatFormatting.GREEN);
    }

    private Component sendLabel() {
        if (!StreamSender.isConfigured()) return tr("menu.send.setup").withStyle(ChatFormatting.GRAY);
        return StreamSender.isSending()
                ? tr("menu.send.on", Settings.channel).withStyle(ChatFormatting.GREEN)
                : tr("menu.send.off", Settings.channel);
    }

    private Component statusLabel() {
        StreamSender.Status status = StreamSender.status();
        String key = "status." + status.name().toLowerCase(Locale.ROOT);
        return switch (status) {
            case IDLE -> Component.empty();
            case SENDING -> tr(key, Settings.channel).withStyle(ChatFormatting.GREEN);
            case RELAY_ERROR -> tr(key, StreamSender.lastResponseCode()).withStyle(ChatFormatting.RED);
            case UNREACHABLE -> tr(key, Settings.relay).withStyle(ChatFormatting.RED);
            default -> tr(key).withStyle(ChatFormatting.RED);
        };
    }

    private void toggleSending() {
        if (StreamSender.isSending()) StreamSender.stop();
        else StreamSender.start();
    }

    private Button button(Component text, Runnable action, int x, int y, int w) {
        return Button.builder(text, b -> {
            action.run();
            Settings.save();
            rebuildWidgets();
        }).bounds(x, y, w, 20).build();
    }

    private Button cycle(Component text, int[] steps, IntSupplier current, IntConsumer setter, int x, int y, int w) {
        return button(text, () -> setter.accept(next(steps, current.getAsInt())), x, y, w);
    }

    private static int next(int[] steps, int current) {
        for (int step : steps) if (step > current) return step;
        return steps[0];
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("blackytv." + key, args);
    }

    private void pick(CaptureSource source) {
        ScreenCapture.select(source);
        Settings.save();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        centered(g, tr("menu.title"), 16);
        if (StreamSender.isSending()) centered(g, statusLabel(), 26);
        if (sources.isEmpty()) centered(g, tr("menu.nothing_found").withStyle(ChatFormatting.RED), 62);
        if (TvTracker.all().isEmpty()) centered(g, tr("menu.no_tv").withStyle(ChatFormatting.GRAY), height - 14);
    }

    private void centered(GuiGraphicsExtractor g, Component text, int y) {
        g.text(font, text, (width - font.width(text)) / 2, y, 0xFFFFFFFF, true);
    }
}
