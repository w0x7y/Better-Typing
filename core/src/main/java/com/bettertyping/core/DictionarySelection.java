package com.bettertyping.core;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Keeps the language dictionary in step with the player's languages and the game's: loads the first
 * installed one, and downloads a pack the player chose that isn't installed, or whose pinned file
 * changed with a mod update. "Game language" never downloads. A download that failed isn't tried again
 * until the player picks that language again or restarts the game.
 *
 * <p>All of it runs on the game thread except loading and downloading, which run on {@code background}.
 * Their results come back through {@code gameThread}, where a load that a newer choice overtook is
 * dropped.
 */
public final class DictionarySelection {
	/** Where a language stands, for showing it in the settings. */
	public enum Status { INSTALLED, DOWNLOADING, FAILED, NOT_INSTALLED }

	/** Hears what happened, on the game thread. */
	public interface Listener {
		void loaded(LanguageDictionary dictionary, long millis);

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
	private List<String> configured = List.of(LanguagePacks.GAME_LANGUAGE);
	private String gameLanguage = LanguagePacks.FALLBACK;
	private List<String> loaded = List.of();
	private int loads;

	public DictionarySelection(LanguagePacks packs, Executor background, Executor gameThread, Listener listener) {
		this.packs = packs;
		this.background = background;
		this.gameThread = gameThread;
		this.listener = listener;
	}

	/**
	 * Brings the dictionary in step with these languages: starts any download they need and loads the
	 * first installed one. Cheap when nothing changed, so it can run on every screen.
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
		List<String> languages = packs.resolve(this.configured, gameLanguage);
		if (languages.equals(loaded)) {
			return;
		}
		loaded = languages;
		int load = ++loads;
		background.execute(() -> load(languages, load));
	}

	/** {@link #update} after the player picked these languages: a download that failed is tried again. */
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

	private void load(List<String> languages, int load) {
		long start = System.nanoTime();
		Optional<LanguageDictionary> dictionary;
		try {
			dictionary = packs.load(languages, (code, reason) -> gameThread.execute(() -> listener.skipped(code, reason)));
		} catch (RuntimeException e) {
			gameThread.execute(() -> listener.skipped(languages.get(0), new IOException("Loading failed", e)));
			return;
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		gameThread.execute(() -> {
			if (load == loads) {
				dictionary.ifPresent(words -> listener.loaded(words, millis));
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
