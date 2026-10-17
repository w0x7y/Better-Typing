package com.bettertyping.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Keeps the language dictionaries in step with the player's languages and the game's: loads every
 * installed one, and downloads a pack the player chose that isn't installed, or whose pinned file
 * changed with a mod update. "Game language" uses the game language's catalog entry and never
 * downloads. English (US) stands in when none of them is installed, or none could be read. A download
 * that failed isn't tried again until {@link #choose} (the settings opening or saving) or a restart.
 *
 * <p>All of it runs on the game thread except loading and downloading, which run on {@code background}.
 * Their results come back through {@code gameThread}, where a load that a newer choice overtook is
 * dropped.
 */
public final class DictionarySelection {
	/** The setting for "use the game's language". */
	public static final String GAME_LANGUAGE = "auto";
	/** Bundled, so there's always a dictionary. */
	private static final String FALLBACK = "en_us";

	/** Where a language stands, for showing it in the settings. */
	public enum Status { INSTALLED, DOWNLOADING, FAILED, NOT_INSTALLED }

	/** Hears what happened, on the game thread. */
	public interface Listener {
		void loaded(List<LanguageDictionary> dictionaries, long millis);

		/** A language was passed over while loading, because it couldn't be read. */
		void skipped(String language, IOException reason);

		void downloadStarted(LanguagePacks.Language language);

		void downloaded(LanguagePacks.Language language);

		void downloadFailed(LanguagePacks.Language language, IOException failure);
	}

	private final LanguagePacks packs;
	private final Executor background;
	private final Executor gameThread;
	private final Listener listener;
	private final Set<String> downloading = new HashSet<>();
	private final Set<String> failed = new HashSet<>();
	private List<String> configured = List.of(GAME_LANGUAGE);
	private String gameLanguage = FALLBACK;
	private List<String> loaded = List.of();
	private int loads;

	public DictionarySelection(LanguagePacks packs, Executor background, Executor gameThread, Listener listener) {
		this.packs = packs;
		this.background = background;
		this.gameThread = gameThread;
		this.listener = listener;
	}

	/**
	 * Brings the dictionaries in step with these languages: starts any download they need and loads the
	 * installed ones. Cheap when nothing changed, so it can run on every screen.
	 */
	public void update(List<String> configured, String gameLanguage) {
		this.configured = List.copyOf(configured);
		this.gameLanguage = gameLanguage;
		for (String code : this.configured) {
			LanguagePacks.Language language = packs.language(code);
			if (language != null && language.pack() != null && !downloading.contains(code) && !failed.contains(code)
				&& !packs.installed(code)) {
				download(language);
			}
		}
		List<String> languages = resolve();
		if (languages.equals(loaded)) {
			return;
		}
		loaded = languages;
		int load = ++loads;
		background.execute(() -> load(languages, load));
	}

	/** {@link #update}, trying a download that failed again: the settings opened or the player saved them. */
	public void choose(List<String> configured, String gameLanguage) {
		failed.removeAll(configured);
		update(configured, gameLanguage);
	}

	public Status status(String code) {
		if (downloading.contains(code)) {
			return Status.DOWNLOADING;
		}
		if (packs.installed(code)) {
			return Status.INSTALLED;
		}
		return failed.contains(code) ? Status.FAILED : Status.NOT_INSTALLED;
	}

	/**
	 * The installed languages to use: {@link #GAME_LANGUAGE} stands for the game language's catalog entry,
	 * and so does an empty list, which the settings file also reads as the default. A language that isn't
	 * installed (yet) is left out, so the list changes once its download finishes.
	 */
	private List<String> resolve() {
		List<String> codes = new ArrayList<>();
		for (String code : configured.isEmpty() ? List.of(GAME_LANGUAGE) : configured) {
			if (code.equals(GAME_LANGUAGE)) {
				code = forGame();
			}
			if (code != null && !codes.contains(code) && packs.installed(code)) {
				codes.add(code);
			}
		}
		if (codes.isEmpty()) {
			codes.add(FALLBACK);
		}
		return codes;
	}

	/** The catalog language used for the game's language (de_de, or en_gb for en_au), or null. */
	private String forGame() {
		String code = gameLanguage.toLowerCase(Locale.ROOT);
		if (packs.language(code) != null) {
			return code;
		}
		for (LanguagePacks.Language language : packs.languages()) {
			if (language.gameLanguages().contains(code)) {
				return language.code();
			}
		}
		return null;
	}

	private void load(List<String> languages, int load) {
		long start = System.nanoTime();
		BiConsumer<String, IOException> skipped = (code, reason) -> gameThread.execute(() -> listener.skipped(code, reason));
		List<LanguageDictionary> dictionaries = packs.load(languages, skipped);
		if (dictionaries.isEmpty() && !languages.contains(FALLBACK)) {
			dictionaries = packs.load(List.of(FALLBACK), skipped);
		}
		List<LanguageDictionary> result = dictionaries;
		long millis = (System.nanoTime() - start) / 1_000_000;
		gameThread.execute(() -> {
			if (load == loads && !result.isEmpty()) {
				listener.loaded(result, millis);
			}
		});
	}

	private void download(LanguagePacks.Language language) {
		downloading.add(language.code());
		listener.downloadStarted(language);
		background.execute(() -> {
			IOException failure = null;
			try {
				packs.download(language.code());
			} catch (IOException e) {
				failure = e;
			} catch (RuntimeException e) {
				failure = new IOException("Download failed", e);
			}
			IOException result = failure;
			gameThread.execute(() -> {
				downloading.remove(language.code());
				if (result == null) {
					listener.downloaded(language);
					update(configured, gameLanguage);
				} else {
					failed.add(language.code());
					listener.downloadFailed(language, result);
				}
			});
		});
	}
}
