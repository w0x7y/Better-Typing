package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DictionarySelectionTest {
	private static final URI RELEASE = URI.create("https://github.example/releases/download/languages-1.1.0/de_de.jar");
	private static final List<String> GERMAN = List.of("de_de");

	@TempDir
	Path dir;

	private final byte[] pack = jar("der\ndie\nHaus\n");
	/** The mod's dictionary folder; reading a file whose content is "unreadable" fails. */
	private final Map<String, String> bundled = new HashMap<>(Map.of("en_us.txt", "the\n", "en_gb.txt", "colour\n"));
	private final List<String> events = new ArrayList<>();
	/** Work for the game thread, run when the test says so. */
	private final Deque<Runnable> gameThread = new ArrayDeque<>();
	/** Work for the background, run right away unless a test holds it back. */
	private final Deque<Runnable> background = new ArrayDeque<>();
	private boolean holdBackground;
	private boolean online = true;
	private int fetches;
	private LanguagePacks packs;
	private DictionarySelection selection;

	@BeforeEach
	void setUp() throws IOException {
		String catalog = "en_us\tEnglish (US)\t\nen_gb\tEnglish (UK)\ten_au\n"
			+ "de_de\tDeutsch\tde_at de_ch\t" + pack.length + "\t" + sha512(pack) + "\t" + RELEASE + "\n";
		packs = new LanguagePacks(dir, LanguagePacks.readCatalog(new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8))),
			file -> {
				if ("unreadable".equals(bundled.get(file))) {
					throw new IOException("Broken " + file);
				}
				return bundled.containsKey(file) ? new ByteArrayInputStream(bundled.get(file).getBytes(StandardCharsets.UTF_8)) : null;
			},
			(uri, maxBytes) -> {
				fetches++;
				if (!online) {
					throw new IOException("offline");
				}
				return new ByteArrayInputStream(pack);
			});
		Executor inBackground = task -> {
			if (holdBackground) {
				background.add(task);
			} else {
				task.run();
			}
		};
		selection = new DictionarySelection(packs, inBackground, gameThread::add, new DictionarySelection.Listener() {
			@Override
			public void loaded(List<LanguageDictionary> dictionaries, long millis) {
				events.add("loaded " + String.join(" ", dictionaries.stream().map(LanguageDictionary::language).toList()));
			}

			@Override
			public void skipped(String language, IOException reason) {
				events.add("skipped " + language);
			}

			@Override
			public void downloadStarted(LanguagePacks.Language language) {
				events.add("downloading " + language.code());
			}

			@Override
			public void downloaded(LanguagePacks.Language language) {
				events.add("downloaded " + language.code());
			}

			@Override
			public void downloadFailed(LanguagePacks.Language language, IOException failure) {
				events.add("failed " + language.code());
			}
		});
	}

	private static byte[] jar(String words) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				zip.putNextEntry(new ZipEntry("assets/bettertyping/dictionaries/de_de.txt"));
				zip.write(words.getBytes(StandardCharsets.UTF_8));
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static String sha512(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	/** Runs the game thread's queue until it's empty. */
	private void runGameThread() {
		while (!gameThread.isEmpty()) {
			gameThread.poll().run();
		}
	}

	@Test
	void loadsEveryChosenLanguageTogether() {
		selection.update(List.of("en_gb", "de_de"), "en_us");
		runGameThread();
		runGameThread();
		assertEquals("loaded en_gb de_de", events.get(events.size() - 1));
	}

	@Test
	void loadsTheInstalledLanguageOnce() {
		selection.update(List.of("en_gb"), "en_us");
		selection.update(List.of("en_gb"), "en_us");
		runGameThread();
		assertEquals(List.of("loaded en_gb"), events);
	}

	@Test
	void gameLanguageFollowsTheGameAndNeverDownloads() {
		selection.update(List.of(DictionarySelection.GAME_LANGUAGE), "en_au");
		runGameThread();
		selection.update(List.of(DictionarySelection.GAME_LANGUAGE), "de_de");
		runGameThread();
		selection.update(List.of(), "en_au");
		runGameThread();
		assertEquals(List.of("loaded en_gb", "loaded en_us", "loaded en_gb"), events, "German isn't installed, so English (US); none chosen is the game language");
		assertEquals(0, fetches);
	}

	@Test
	void gameLanguageUsesItsCatalogEntryOnceInstalled() {
		selection.choose(GERMAN, "en_us");
		runGameThread();
		runGameThread();
		selection.update(List.of(DictionarySelection.GAME_LANGUAGE), "de_CH");
		runGameThread();
		assertEquals("loaded de_de", events.get(events.size() - 1), "Swiss German uses the German pack");
		selection.update(List.of("en_gb", "de_de", DictionarySelection.GAME_LANGUAGE), "de_at");
		runGameThread();
		assertEquals("loaded en_gb de_de", events.get(events.size() - 1), "each language once");
	}

	@Test
	void englishStandsInWhenNothingChosenIsInstalledOrReadable() {
		holdBackground = true;
		selection.update(List.of("xx_xx", "../de_de", "de_de"), "en_us");
		background.removeLast().run();
		runGameThread();
		assertEquals(List.of("downloading de_de", "loaded en_us"), events, "the download is still running");

		events.clear();
		bundled.put("en_gb.rules.txt", "unreadable");
		selection.update(List.of("en_gb"), "en_us");
		background.removeLast().run();
		runGameThread();
		assertEquals(List.of("skipped en_gb", "loaded en_us"), events);
	}

	@Test
	void aLoadThatANewerChoiceOvertookIsDropped() {
		selection.update(List.of("en_gb"), "en_us");
		selection.update(List.of("en_us"), "en_us");
		runGameThread();
		assertEquals(List.of("loaded en_us"), events);
	}

	@Test
	void chosenPackIsDownloadedThenLoaded() {
		selection.update(GERMAN, "en_us");
		runGameThread();
		runGameThread();
		assertEquals(List.of("downloading de_de", "downloaded de_de", "loaded de_de"), events);
		assertEquals(DictionarySelection.Status.INSTALLED, selection.status("de_de"));
	}

	@Test
	void statusShowsADownloadInProgress() {
		holdBackground = true;
		selection.update(GERMAN, "en_us");
		assertEquals(DictionarySelection.Status.DOWNLOADING, selection.status("de_de"));
		selection.update(GERMAN, "en_us");
		assertEquals(2, background.size(), "one download and one load, however often it's updated");
	}

	@Test
	void failedDownloadWaitsUntilTheLanguageIsPickedAgain() {
		online = false;
		selection.update(GERMAN, "en_us");
		runGameThread();
		assertEquals(DictionarySelection.Status.FAILED, selection.status("de_de"));
		selection.update(GERMAN, "en_us");
		selection.update(GERMAN, "en_us");
		assertEquals(1, fetches, "opening more screens doesn't retry");

		online = true;
		selection.choose(GERMAN, "en_us");
		runGameThread();
		runGameThread();
		assertEquals(2, fetches);
		assertEquals("loaded de_de", events.get(events.size() - 1));
	}

	@Test
	void packWhosePinnedFileChangedIsDownloadedAgain() throws IOException {
		Files.write(dir.resolve("de_de.jar"), jar("der\ndie\n"));
		selection.update(GERMAN, "en_us");
		runGameThread();
		runGameThread();
		assertEquals(1, fetches);
		assertEquals("loaded de_de", events.get(events.size() - 1));
	}
}
