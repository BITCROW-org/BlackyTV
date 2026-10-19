package org.bitcrow.blackytv;

import com.mojang.blaze3d.platform.InputConstants;
import org.bitcrow.blackytv.capture.AudioCapture;
import org.bitcrow.blackytv.capture.ScreenCapture;
import org.bitcrow.blackytv.command.BlackyTvCommand;
import org.bitcrow.blackytv.config.Settings;
import org.bitcrow.blackytv.control.ControlScreen;
import org.bitcrow.blackytv.control.TapControl;
import org.bitcrow.blackytv.gui.TvScreen;
import org.bitcrow.blackytv.tv.TvRenderer;
import org.bitcrow.blackytv.tv.TvTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackyTV implements ClientModInitializer {

    public static final String MOD_ID = "blackytv";
    private static final Logger LOG = LoggerFactory.getLogger("BlackyTV");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        Settings.load();

        KeyMapping.Category category = KeyMapping.Category.register(id("main"));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.blackytv.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_N, category));
        KeyMapping controlKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.blackytv.control", InputConstants.Type.KEYSYM, InputConstants.KEY_G, category));

        ClientTickEvents.START_CLIENT_TICK.register(TapControl::tick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) mc.setScreenAndShow(new TvScreen());
            while (controlKey.consumeClick()) ControlScreen.open(mc);
            TvTracker.tick(mc);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> BlackyTvCommand.register(dispatcher));
        LevelRenderEvents.START_MAIN.register(context -> TvRenderer.upload());
        LevelRenderEvents.COLLECT_SUBMITS.register(TvRenderer::render);

        if (ScreenCapture.isSupported()) {
            ScreenCapture.start();
            AudioCapture.start();
        } else {
            LOG.info("Capturing is only supported on Windows, watching still works");
        }
    }
}
