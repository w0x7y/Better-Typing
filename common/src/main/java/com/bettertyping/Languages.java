package com.bettertyping;

import com.bettertyping.core.DictionarySelection;
import com.bettertyping.core.LanguageDictionary;
import com.bettertyping.core.LanguagePacks;
import com.bettertyping.version.Toasts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Connects the {@link DictionarySelection} to the game: the settings, the game's language, the spell
 * checker, toasts and the log.
 */
final class Languages {
	private static final String DICTIONARIES = "/assets/bettertyping/dictionaries/";
	private static final String CATALOG = "/assets/bettertyping/languages.txt";
	// A download gets this long to finish, however slowly the bytes arrive.
	private static final Duration DOWNLOAD_DEADLINE = Duration.ofMinutes(2);

	private static LanguagePacks packs;
	private static DictionarySelection selection;

	private Languages() {
	}

	static void init(Path downloads) {
		List<LanguagePacks.Language> catalog = List.of();
		try (InputStream in = Languages.class.getResourceAsStream(CATALOG)) {
			if (in == null) {
				throw new IOException(CATALOG + " is missing");
			}
			catalog = LanguagePacks.readCatalog(in);
		} catch (IOException e) {
			BetterTyping.LOGGER.error("Couldn't read the language catalog", e);
		}
		String version = FabricLoader.getInstance().getModContainer(BetterTyping.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		String userAgent = "w0x7y/Better-Typing/" + version + " (https://github.com/w0x7y/Better-Typing)";
		// Packs installed as mods are on the classpath too, so they're found like the bundled lists.
		packs = new LanguagePacks(downloads, catalog, file -> Languages.class.getResourceAsStream(DICTIONARIES + file),
			LanguagePacks.standardHttp(userAgent, DOWNLOAD_DEADLINE));
		selection = new DictionarySelection(packs, Executors.newCachedThreadPool(task -> {
			Thread thread = new Thread(task, "Better Typing languages");
			thread.setDaemon(true);
			return thread;
		}), task -> Minecraft.getInstance().execute(task), new Reporter());
	}

	static LanguagePacks packs() {
		return packs;
	}

	/** Brings the dictionary in step with the settings and the game's language. Cheap; game thread. */
	static void refresh() {
		selection.update(BetterTyping.settings().languages, gameLanguage());
	}

	/** The player picked {@code code}: uses it from now on, downloading it if needed, even after a failure. Game thread. */
	static void choose(String code) {
		BetterTyping.settings().languages = new ArrayList<>(List.of(code));
		selection.choose(BetterTyping.settings().languages, gameLanguage());
	}

	/** Tries a failed download of the chosen language again; the settings screen calls it when it opens. Game thread. */
	static void retryFailed() {
		List<String> configured = BetterTyping.settings().languages;
		if (configured.stream().anyMatch(code -> selection.status(code) == DictionarySelection.Status.FAILED)) {
			selection.choose(configured, gameLanguage());
		}
	}

	/** How a choice is shown in the settings: its name, and how its download stands. */
	static Component label(String code) {
		if (code.equals(LanguagePacks.GAME_LANGUAGE)) {
			return Component.translatable("bettertyping.settings.language.game");
		}
		LanguagePacks.Language language = packs.language(code);
		if (language == null) {
			return Component.literal(code);
		}
		if (language.pack() == null) {
			return Component.literal(language.name());
		}
		String megabytes = String.format(Locale.ROOT, "%.1f", language.pack().size() / 1_000_000.0);
		return switch (selection.status(code)) {
			case INSTALLED -> Component.literal(language.name());
			case DOWNLOADING -> Component.translatable("bettertyping.settings.language.downloading", language.name());
			case FAILED -> Component.translatable("bettertyping.settings.language.failed", language.name(), megabytes);
			case NOT_INSTALLED -> Component.translatable("bettertyping.settings.language.download", language.name(), megabytes);
		};
	}

	private static String gameLanguage() {
		return Minecraft.getInstance().getLanguageManager().getSelected();
	}

	/** Shows what the selection did. */
	private static final class Reporter implements DictionarySelection.Listener {
		@Override
		public void loaded(LanguageDictionary dictionary, long millis) {
			BetterTyping.checker().setLanguage(dictionary.words());
			BetterTyping.LOGGER.info("Loaded {} dictionary: {} words in {} ms", dictionary.language(), dictionary.words().size(), millis);
		}

		@Override
		public void skipped(String language, IOException reason) {
			BetterTyping.LOGGER.warn("Couldn't load the {} dictionary: {}", language, reason.toString());
		}

		@Override
		public void downloadStarted(LanguagePacks.Language language) {
			toast("bettertyping.toast.downloading", language);
		}

		@Override
		public void downloaded(LanguagePacks.Language language) {
			toast("bettertyping.toast.downloaded", language);
		}

		@Override
		public void downloadFailed(LanguagePacks.Language language, IOException failure) {
			BetterTyping.LOGGER.warn("Couldn't download the {} language pack", language.code(), failure);
			toast("bettertyping.toast.download_failed", language);
		}

		private static void toast(String key, LanguagePacks.Language language) {
			Toasts.show(Minecraft.getInstance(), Component.translatable("bettertyping.name"), Component.translatable(key, language.name()));
		}
	}
}
