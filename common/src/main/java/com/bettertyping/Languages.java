package com.bettertyping;

import com.bettertyping.core.DictionarySelection;
import com.bettertyping.core.LanguageDictionary;
import com.bettertyping.core.LanguagePacks;
import com.bettertyping.core.StandardHttp;
import com.bettertyping.version.Screens;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Connects the {@link DictionarySelection} to the game: the settings, the game's language, the spell
 * checker, toasts and the log.
 */
final class Languages {
	private static final String DICTIONARIES = "/assets/bettertyping/dictionaries/";
	private static final String CATALOG = "/assets/bettertyping/languages.txt";
	// A download gets this long to finish, however slowly the bytes arrive.
	private static final Duration DOWNLOAD_DEADLINE = Duration.ofMinutes(2);
	// Flags as private-use characters, with the default font for everything else; see tools/make_flags.py.
	private static final FontDescription FLAGS = new FontDescription.Resource(Identifier.fromNamespaceAndPath(BetterTyping.MOD_ID, "flags"));
	private static final int GLOBE = 0xE2A4;

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
			new StandardHttp(userAgent, DOWNLOAD_DEADLINE));
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

	/** The settings opened or were saved: uses their languages, downloading any that's missing, even after a failure. Game thread. */
	static void choose() {
		selection.choose(BetterTyping.settings().languages, gameLanguage());
	}

	/**
	 * How a choice is named in the settings: its flag, the country in its code, and its name. The flag is a
	 * character rather than a sprite, because YACL draws option names from their plain text.
	 */
	static Component name(String code) {
		if (code.equals(DictionarySelection.GAME_LANGUAGE)) {
			return Component.literal(Character.toString(GLOBE) + " ")
				.append(Component.translatable("bettertyping.settings.language.game"))
				.withStyle(style -> style.withFont(FLAGS));
		}
		LanguagePacks.Language language = packs.language(code);
		if (language == null) {
			return Component.literal(code);
		}
		return Component.literal(language.flag() + " " + language.name()).withStyle(style -> style.withFont(FLAGS));
	}

	/** Whether a choice is on, and for a ticked pack how its download stands. */
	static Component status(String code, boolean on) {
		LanguagePacks.Language language = packs.language(code);
		if (!on || language == null || language.pack() == null) {
			return CommonComponents.optionStatus(on);
		}
		String megabytes = String.format(Locale.ROOT, "%.1f", language.pack().size() / 1_000_000.0);
		return switch (selection.status(code)) {
			case INSTALLED -> CommonComponents.optionStatus(true);
			case DOWNLOADING -> Component.translatable("bettertyping.settings.language.downloading");
			case FAILED -> Component.translatable("bettertyping.settings.language.failed", megabytes);
			case NOT_INSTALLED -> Component.translatable("bettertyping.settings.language.download", megabytes);
		};
	}

	private static String gameLanguage() {
		return Minecraft.getInstance().getLanguageManager().getSelected();
	}

	/** Shows what the selection did. */
	private static final class Reporter implements DictionarySelection.Listener {
		private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

		@Override
		public void loaded(List<LanguageDictionary> dictionaries, long millis) {
			BetterTyping.checker().setLanguages(dictionaries.stream().map(LanguageDictionary::words).toList());
			BetterTyping.LOGGER.info("Loaded dictionaries in {} ms: {}", millis, dictionaries.stream()
				.map(dictionary -> dictionary.language() + " (" + dictionary.words().size() + " words)").toList());
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

		/** Shows a toast, replacing the mod's previous one if it's still up. */
		private static void toast(String key, LanguagePacks.Language language) {
			SystemToast.addOrUpdate(Screens.toasts(Minecraft.getInstance()), TOAST, Component.translatable("bettertyping.name"),
				Component.translatable(key, language.name()));
		}
	}
}
