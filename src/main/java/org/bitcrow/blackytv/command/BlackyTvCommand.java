package org.bitcrow.blackytv.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import org.bitcrow.blackytv.capture.CaptureSource;
import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.config.Settings;
import org.bitcrow.blackytv.stream.RelayAddress;
import org.bitcrow.blackytv.stream.StreamSender;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.IntConsumer;

public final class BlackyTvCommand {

    private static final String CHANNEL_PATTERN = "[a-z0-9_-]{1,32}";
    private static final int MIN_PASSWORD_LENGTH = 4;

    private BlackyTvCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        IntConsumer volume = v -> Settings.volume = v;
        IntConsumer streamVolume = v -> Settings.streamVolume = v;

        dispatcher.register(ClientCommands.literal("blackytv")
                .then(ClientCommands.literal("stream")
                        .then(ClientCommands.argument("channel", StringArgumentType.word())
                                .then(ClientCommands.argument("password", StringArgumentType.word())
                                        .executes(BlackyTvCommand::stream))))
                .then(ClientCommands.literal("stop").executes(BlackyTvCommand::stop))
                .then(ClientCommands.literal("relay")
                        .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                                .executes(BlackyTvCommand::relay)))
                .then(percent("volume", volume, "blackytv.command.volume"))
                .then(percent("lautstaerke", volume, "blackytv.command.volume"))
                .then(percent("streamvolume", streamVolume, "blackytv.command.stream_volume"))
                .then(percent("streamton", streamVolume, "blackytv.command.stream_volume")));
    }

    public static Component message(Component text) {
        return Component.literal("BlackyTV").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(text);
    }

    private static int stream(CommandContext<FabricClientCommandSource> context) {
        String channel = StringArgumentType.getString(context, "channel").toLowerCase();
        String password = StringArgumentType.getString(context, "password");
        if (!channel.matches(CHANNEL_PATTERN))
            return fail(context, Component.translatable("blackytv.command.invalid_channel"));
        if (password.length() < MIN_PASSWORD_LENGTH)
            return fail(context, Component.translatable("blackytv.command.short_password", MIN_PASSWORD_LENGTH));

        Settings.channel = channel;
        Settings.password = password;
        Settings.save();
        StreamSender.start();

        CaptureSource source = ScreenCapture.source();
        Component text = source == null
                ? Component.translatable("blackytv.command.no_source", highlight(channel)).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("blackytv.command.streaming", highlight(source.name()), highlight(channel));
        context.getSource().sendFeedback(message(text));
        return 1;
    }

    private static int stop(CommandContext<FabricClientCommandSource> context) {
        StreamSender.stop();
        context.getSource().sendFeedback(message(Component.translatable("blackytv.command.stopped")));
        return 1;
    }

    private static int relay(CommandContext<FabricClientCommandSource> context) {
        Settings.relay = RelayAddress.resolve(StringArgumentType.getString(context, "url").trim());
        Settings.save();
        context.getSource().sendFeedback(message(Component.translatable("blackytv.command.relay", highlight(Settings.relay))));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> percent(String name, IntConsumer setter, String key) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 200)).executes(context -> {
                    int value = IntegerArgumentType.getInteger(context, "percent");
                    setter.accept(value);
                    Settings.save();
                    context.getSource().sendFeedback(message(Component.translatable(key, highlight(value + "%"))));
                    return 1;
                }));
    }

    private static MutableComponent highlight(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static int fail(CommandContext<FabricClientCommandSource> context, MutableComponent text) {
        context.getSource().sendFeedback(message(text.withStyle(ChatFormatting.RED)));
        return 0;
    }
}
