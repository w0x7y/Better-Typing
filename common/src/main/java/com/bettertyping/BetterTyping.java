package com.bettertyping;

import com.bettertyping.core.PersonalDictionary;
import com.bettertyping.core.Settings;
import com.bettertyping.core.SpellChecker;
import com.bettertyping.core.WordList;
import com.bettertyping.version.Screens;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
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
	private static final String DICTIONARY_PATH = "/assets/bettertyping/dictionaries/%s.txt";
	private static final String CHAT_WORDS_PATH = "/assets/bettertyping/dictionaries/%s.chat.txt";

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

		PersonalDictionary personal = new PersonalDictionary(configDir.resolve("personal_dictionary.txt"));
		try {
			personal.load();
		} catch (IOException e) {
			LOGGER.error("Couldn't read the personal dictionary", e);
		}
		checker = new SpellChecker(personal);
		CompletableFuture.runAsync(BetterTyping::loadLanguage);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenHooks.attach(screen));
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

	/** Loads the first configured language that ships with the mod. Nothing is flagged until this finishes. */
	private static void loadLanguage() {
		for (String language : settings.languages) {
			try (InputStream in = BetterTyping.class.getResourceAsStream(DICTIONARY_PATH.formatted(language));
				InputStream chat = BetterTyping.class.getResourceAsStream(CHAT_WORDS_PATH.formatted(language))) {
				if (in == null) {
					LOGGER.warn("No bundled dictionary for language {}", language);
					continue;
				}
				long start = System.nanoTime();
				WordList words = WordList.language(in, chat != null ? chat : InputStream.nullInputStream());
				checker.setLanguage(words);
				LOGGER.info("Loaded {} dictionary: {} words in {} ms", language, words.size(), (System.nanoTime() - start) / 1_000_000);
				return;
			} catch (IOException e) {
				LOGGER.error("Couldn't load dictionary {}", language, e);
			}
		}
	}

	public static Settings settings() {
		return settings;
	}

	public static SpellChecker checker() {
		return checker;
	}
}
