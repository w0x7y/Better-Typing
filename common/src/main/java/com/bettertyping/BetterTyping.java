package com.bettertyping;

import com.bettertyping.core.Settings;
import com.bettertyping.core.SpellChecker;
import com.bettertyping.version.Screens;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterTyping implements ClientModInitializer {
	public static final String MOD_ID = "bettertyping";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Settings settings = new Settings();
	private static Path settingsFile;
	private static SpellChecker checker;
	/** Set by /bettertyping; the screen opens on the next tick, once the chat screen has closed. */
	private static volatile boolean openSettings;

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		settingsFile = configDir.resolve("settings.properties");
		try {
			settings = Settings.load(settingsFile);
		} catch (IOException e) {
			LOGGER.error("Couldn't read settings, using defaults", e);
		}

		checker = new SpellChecker(configDir.resolve("personal_dictionary.txt"), e -> LOGGER.error(e.getMessage(), e.getCause()));
		Languages.init(configDir.resolve("dictionaries"));

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			// The game's language can only change between screens. Nothing is flagged until a dictionary has loaded.
			Languages.refresh();
			// Item and mob names exist (and are translated) once a world is loaded.
			if (client.level != null) {
				GameWords.refreshIfNeeded(checker);
			}
			ScreenHooks.attach(screen);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bettertyping").executes(context -> {
				openSettings = true;
				return 1;
			})));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openSettings) {
				openSettings = false;
				Screens.open(client, SettingsScreen.create(null));
			}
		});
	}

	public static void saveSettings() {
		try {
			settings.save(settingsFile);
		} catch (IOException e) {
			LOGGER.error("Couldn't save settings", e);
		}
	}

	public static Settings settings() {
		return settings;
	}

	public static SpellChecker checker() {
		return checker;
	}
}
