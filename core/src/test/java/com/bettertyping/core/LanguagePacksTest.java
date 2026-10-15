package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguagePacksTest {
	private static final URI MIRROR = URI.create("https://mirror.example/de_de.jar");
	private static final URI BACKUP = URI.create("https://backup.example/de_de.jar");

	@TempDir
	Path dir;

	private final byte[] pack = jar("de_de.txt", "der\ndie\nHaus\n");
	/** What each URL answers; a missing URL fails like a network error. */
	private final Map<URI, byte[]> web = new HashMap<>();
	private final List<URI> fetched = new ArrayList<>();
	private LanguagePacks packs;

	@BeforeEach
	void setUp() throws IOException {
		String catalog = String.join("\n",
			"# code\tname\tgame languages\tsize\tsha512\turls",
			"en_us\tEnglish (US)\ten_ca en_pt",
			"en_gb\tEnglish (UK)\ten_au en_nz",
			"de_de\tDeutsch\tde_at de_ch\t" + pack.length + "\t" + sha512(pack) + "\t" + MIRROR + " " + BACKUP,
			"");
		Map<String, String> bundled = Map.of("en_us.txt", "the\nhello\n", "en_us.chat.txt", "gg\n", "en_gb.txt", "colour\n");
		packs = new LanguagePacks(dir.resolve("dictionaries"), LanguagePacks.readCatalog(stream(catalog)),
			name -> bundled.containsKey(name) ? stream(bundled.get(name)) : null,
			uri -> {
				fetched.add(uri);
				if (!web.containsKey(uri)) {
					throw new IOException("Can't reach " + uri);
				}
				return new ByteArrayInputStream(web.get(uri));
			});
	}

	private static ByteArrayInputStream stream(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	/** A pack jar holding one dictionary file. */
	private static byte[] jar(String name, String words) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
				zip.putNextEntry(new ZipEntry("fabric.mod.json"));
				zip.write("{}".getBytes(StandardCharsets.UTF_8));
				zip.putNextEntry(new ZipEntry("assets/bettertyping/dictionaries/" + name));
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

	private LanguageDictionary load(String... languages) {
		return LanguageDictionary.loadFirst(List.of(languages), packs.source(), (language, e) -> { }).orElseThrow();
	}

	@Test
	void downloadedPackIsUsed() throws IOException {
		web.put(MIRROR, pack);
		assertFalse(packs.installed("de_de"));
		packs.download("de_de");
		assertTrue(packs.installed("de_de"));
		LanguageDictionary german = load("de_de");
		assertEquals("de_de", german.language());
		assertTrue(german.words().contains("Haus"));
		assertFalse(german.words().contains("haus"));
	}

	@Test
	void triesTheNextUrlWhenOneFails() throws IOException {
		web.put(BACKUP, pack);
		packs.download("de_de");
		assertEquals(List.of(MIRROR, BACKUP), fetched);
		assertTrue(packs.installed("de_de"));
	}

	@Test
	void fileThatIsNotThePinnedOneIsRejected() throws IOException {
		byte[] other = jar("de_de.txt", "der\ndie\nHauss\n");
		web.put(MIRROR, other);
		web.put(BACKUP, new byte[pack.length + 1]);
		IOException failure = assertThrows(IOException.class, () -> packs.download("de_de"));
		assertEquals(2, failure.getSuppressed().length);
		assertFalse(packs.installed("de_de"));
		try (var files = Files.list(dir.resolve("dictionaries"))) {
			assertEquals(List.of(), files.toList(), "no partial files are left");
		}
	}

	@Test
	void changedFileOnDiskIsNotUsed() throws IOException {
		web.put(MIRROR, pack);
		packs.download("de_de");
		byte[] tampered = jar("de_de.txt", "der\ndie\nHauss\n");
		Files.write(dir.resolve("dictionaries/de_de.jar"), tampered);
		LanguagePacks restarted = new LanguagePacks(dir.resolve("dictionaries"), packs.languages(), name -> null, uri -> {
			throw new IOException("offline");
		});
		assertFalse(restarted.installed("de_de"));
		assertNull(restarted.source().open("de_de.txt"));
	}

	@Test
	void downloadingAgainKeepsTheFile() throws IOException {
		web.put(MIRROR, pack);
		packs.download("de_de");
		packs.download("de_de");
		assertEquals(List.of(MIRROR), fetched);
		assertArrayEquals(pack, Files.readAllBytes(dir.resolve("dictionaries/de_de.jar")));
	}

	@Test
	void bundledLanguagesNeedNoDownload() {
		assertTrue(packs.installed("en_gb"));
		assertThrows(IOException.class, () -> packs.download("en_gb"));
		assertTrue(load("en_us").words().contains("gg"), "bundled chat words still load");
	}

	@Test
	void gameLanguagesMapToCatalogLanguages() {
		assertEquals("en_gb", packs.forGame("en_AU").code());
		assertEquals("de_de", packs.forGame("de_ch").code());
		assertEquals("de_de", packs.forGame("de_de").code());
		assertNull(packs.forGame("pt_pt"));
	}

	@Test
	void gameLanguageIsOnlyUsedWhenInstalled() throws IOException {
		assertEquals(List.of("en_gb", "en_us"), packs.resolve(List.of(LanguagePacks.GAME_LANGUAGE), "en_nz"));
		assertEquals(List.of("en_us"), packs.resolve(List.of(LanguagePacks.GAME_LANGUAGE), "de_de"));
		web.put(MIRROR, pack);
		packs.download("de_de");
		assertEquals(List.of("de_de", "en_us"), packs.resolve(List.of(LanguagePacks.GAME_LANGUAGE), "de_de"));
		assertEquals(List.of("en_us"), packs.resolve(List.of("en_us", LanguagePacks.GAME_LANGUAGE), "en_us"));
	}

	@Test
	void chosenPackIsUsedOnceDownloaded() throws IOException {
		assertEquals(List.of("en_us"), packs.resolve(List.of("de_de"), "en_us"));
		web.put(MIRROR, pack);
		packs.download("de_de");
		assertEquals(List.of("de_de", "en_us"), packs.resolve(List.of("de_de"), "en_us"));
		assertEquals(List.of("en_us"), packs.resolve(List.of("xx_xx", "../de_de"), "en_us"));
	}

	@Test
	void bundledCatalogIsValid() throws IOException {
		List<LanguagePacks.Language> languages;
		try (InputStream in = Files.newInputStream(Path.of("../common/src/main/resources/assets/bettertyping/languages.txt"))) {
			languages = LanguagePacks.readCatalog(in);
		}
		assertEquals(List.of("en_us", "en_gb"), languages.stream().filter(l -> l.pack() == null).map(LanguagePacks.Language::code).toList());
		for (LanguagePacks.Language language : languages) {
			assertTrue(Files.exists(Path.of("../common/src/main/resources/assets/bettertyping/dictionaries/" + language.code() + ".txt"))
				!= (language.pack() != null), language.code() + " is either bundled or a pack");
			if (language.pack() != null) {
				assertEquals(2, language.pack().urls().size(), language.code() + " has a Modrinth and a GitHub URL");
			}
		}
		assertEquals(languages.size(), languages.stream().map(LanguagePacks.Language::code).distinct().count());
	}

	@Test
	void catalogRejectsInsecureOrBrokenLines() {
		String hash = "a".repeat(128);
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("de_de\tDeutsch\t\t10\t" + hash + "\thttp://example.com/x.jar\n")));
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("de_de\tDeutsch\t\t10\tabc\thttps://example.com/x.jar\n")));
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("../x\tBad\t\n")));
	}

	@Test
	void settingsCanNotNameAFile() throws IOException {
		List<String> skipped = new ArrayList<>();
		assertTrue(LanguageDictionary.loadFirst(List.of("../../secret"), packs.source(), (language, e) -> skipped.add(language)).isEmpty());
		assertEquals(List.of("../../secret"), skipped);
		assertFalse(packs.installed("../de_de"));
	}
}
