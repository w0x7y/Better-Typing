package com.bettertyping;

import com.bettertyping.core.PersonalDictionary;
import com.bettertyping.core.Settings;
import com.bettertyping.core.SpellChecker;
import com.bettertyping.core.WordList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterTyping implements ClientModInitializer {
	public static final String MOD_ID = "bettertyping";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String DICTIONARY_PATH = "/assets/bettertyping/dictionaries/%s.txt";

	private static Settings settings = new Settings();
	private static SpellChecker checker;

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		try {
			settings = Settings.load(configDir.resolve("settings.properties"));
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
	}

	/** Loads the first configured language that ships with the mod. Nothing is flagged until this finishes. */
	private static void loadLanguage() {
		for (String language : settings.languages) {
			String path = DICTIONARY_PATH.formatted(language);
			try (InputStream in = BetterTyping.class.getResourceAsStream(path)) {
				if (in == null) {
					LOGGER.warn("No bundled dictionary for language {}", language);
					continue;
				}
				long start = System.nanoTime();
				WordList words = WordList.fromRankedLines(in);
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
