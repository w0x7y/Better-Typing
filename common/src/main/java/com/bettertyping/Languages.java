package com.bettertyping;

import com.bettertyping.core.LanguageDictionary;
import com.bettertyping.core.LanguagePacks;
import com.bettertyping.version.Toasts;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Which language dictionary is loaded. It follows the settings and the game's language, and a language
 * pack is downloaded in the background when the player picks one that isn't installed.
 */
final class Languages {
	private static final String DICTIONARIES = "/assets/bettertyping/dictionaries/";
	private static final String CATALOG = "/assets/bettertyping/languages.txt";

	private static LanguagePacks packs;
	private static final Set<String> downloading = ConcurrentHashMap.newKeySet();
	/** Counts loads, so a slow load that finishes after a newer one is dropped. */
	private static final AtomicInteger loads = new AtomicInteger();
	/** The languages last loaded (or loading). Game thread only. */
	private static List<String> loaded = List.of();

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
		packs = new LanguagePacks(downloads, catalog, name -> Languages.class.getResourceAsStream(DICTIONARIES + name),
			LanguagePacks.standardHttp(userAgent));
	}

	static LanguagePacks packs() {
		return packs;
	}

	/** Loads the dictionary for the settings and the game's language, unless it's loaded or loading already. Game thread. */
	static void refresh() {
		List<String> languages = packs.resolve(BetterTyping.settings().languages,
			Minecraft.getInstance().getLanguageManager().getSelected());
		if (languages.equals(loaded)) {
			return;
		}
		loaded = languages;
		int load = loads.incrementAndGet();
		CompletableFuture.runAsync(() -> load(languages, load));
	}

	/** Uses {@code code} from now on, downloading its pack first if it isn't installed. Game thread. */
	static void choose(String code) {
		BetterTyping.settings().languages = new ArrayList<>(List.of(code));
		LanguagePacks.Language language = packs.language(code);
		if (language != null && language.pack() != null && !packs.installed(code)) {
			download(language);
		}
		refresh();
	}

	/** How a choice is shown in the settings: its name, and the download size if it isn't installed. */
	static Component label(String code) {
		if (code.equals(LanguagePacks.GAME_LANGUAGE)) {
			return Component.translatable("bettertyping.settings.language.game");
		}
		LanguagePacks.Language language = packs.language(code);
		if (language == null) {
			return Component.literal(code);
		}
		if (language.pack() != null && !packs.installed(code)) {
			String megabytes = String.format(Locale.ROOT, "%.1f", language.pack().size() / 1_000_000.0);
			return Component.translatable("bettertyping.settings.language.download", language.name(), megabytes);
		}
		return Component.literal(language.name());
	}

	private static void load(List<String> languages, int load) {
		long start = System.nanoTime();
		LanguageDictionary.loadFirst(languages, packs.source(),
				(language, e) -> BetterTyping.LOGGER.warn("Couldn't load the {} dictionary: {}", language, e.toString()))
			.ifPresent(dictionary -> {
				if (load == loads.get()) {
					BetterTyping.checker().setLanguage(dictionary.words());
					BetterTyping.LOGGER.info("Loaded {} dictionary: {} words in {} ms", dictionary.language(), dictionary.words().size(),
						(System.nanoTime() - start) / 1_000_000);
				}
			});
	}

	/** Downloads off the game thread with toasts for how it went, then loads the language if it's still chosen. */
	private static void download(LanguagePacks.Language language) {
		if (!downloading.add(language.code())) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		toast(client, "bettertyping.toast.downloading", language.name());
		CompletableFuture.runAsync(() -> {
			try {
				packs.download(language.code());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}).whenComplete((ignored, failure) -> client.execute(() -> {
			downloading.remove(language.code());
			if (failure == null) {
				toast(client, "bettertyping.toast.downloaded", language.name());
				refresh();
			} else {
				Throwable cause = failure.getCause() instanceof UncheckedIOException e ? e.getCause() : failure;
				BetterTyping.LOGGER.warn("Couldn't download the {} language pack", language.code(), cause);
				toast(client, "bettertyping.toast.download_failed", language.name());
			}
		}));
	}

	private static void toast(Minecraft client, String key, String language) {
		Toasts.show(client, Component.translatable("bettertyping.name"), Component.translatable(key, language));
	}
}
