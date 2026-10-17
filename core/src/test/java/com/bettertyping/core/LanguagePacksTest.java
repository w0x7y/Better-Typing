package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	private static final URI RELEASE = URI.create("https://github.example/releases/download/languages-1.0.0/de_de.jar");

	@TempDir
	Path dir;

	private final byte[] pack = jar("de_de.txt", "der\ndie\nHaus\nTür\n");
	/** The mod's dictionary folder; reading a file whose content is "unreadable" fails, and "crash" throws. */
	private final Map<String, String> bundled = new HashMap<>(Map.of("en_us.txt", "the\nhello\n", "en_us.chat.txt", "hello\ngg\n",
		"chat.txt", "afk\n", "en_gb.txt", "colour\n"));
	/** What each URL answers; a missing URL fails like a network error. */
	private final Map<URI, byte[]> web = new HashMap<>();
	private final List<String> skipped = new ArrayList<>();
	private final List<URI> fetched = new ArrayList<>();
	private LanguagePacks packs;

	@BeforeEach
	void setUp() throws IOException {
		String catalog = String.join("\n",
			"# code\tname\tgame languages\tsize\tsha512\turl",
			"en_us\tEnglish (US)\ten_ca en_pt",
			"en_gb\tEnglish (UK)\ten_au en_nz",
			"de_de\tDeutsch\tde_at de_ch\t" + pack.length + "\t" + sha512(pack) + "\t" + RELEASE,
			"");
		packs = new LanguagePacks(dir.resolve("dictionaries"), LanguagePacks.readCatalog(stream(catalog)), this::openBundled,
			(uri, maxBytes) -> {
				fetched.add(uri);
				if (!web.containsKey(uri)) {
					throw new IOException("Can't reach " + uri);
				}
				return new ByteArrayInputStream(web.get(uri));
			});
	}

	private InputStream openBundled(String file) throws IOException {
		String content = bundled.get(file);
		if ("unreadable".equals(content)) {
			throw new IOException("Broken " + file);
		}
		if ("crash".equals(content)) {
			throw new IllegalStateException("Bug reading " + file);
		}
		return content == null ? null : stream(content);
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

	private List<LanguageDictionary> load(String... languages) {
		return packs.load(List.of(languages), (language, e) -> skipped.add(language));
	}

	private static List<String> codes(List<LanguageDictionary> dictionaries) {
		return dictionaries.stream().map(LanguageDictionary::language).toList();
	}

	@Test
	void loadsEveryLanguageThatHasAWordList() {
		assertEquals(List.of("en_us", "en_gb"), codes(load("xx_xx", "en_us", "en_gb")));
		assertEquals(List.of("xx_xx"), skipped);
	}

	@Test
	void everyLanguageGetsItsChatWordsAndTheSharedOnes() {
		LanguageDictionary english = load("en_us").get(0);
		assertTrue(english.words().contains("gg"));
		assertTrue(english.words().contains("afk"));
		assertTrue(load("en_gb").get(0).words().contains("afk"), "a language without its own chat words still gets the shared ones");
	}

	@Test
	void downloadedPackUsesTheModsChatWordsAndRules() throws IOException {
		bundled.put("de_de.chat.txt", "digga\n");
		bundled.put("de_de.rules.txt", "compound.min_part=3\n");
		web.put(RELEASE, pack);
		packs.download("de_de");
		LanguageDictionary german = load("de_de").get(0);
		assertTrue(german.words().contains("digga"));
		assertTrue(german.words().contains("afk"));
		assertTrue(german.words().contains("Haustür"), "the rules file turns compounds on");
	}

	@Test
	void unreadableLanguageIsSkipped() {
		bundled.put("en_gb.txt", "unreadable");
		assertEquals(List.of("en_us"), codes(load("en_gb", "en_us")));
		assertEquals(List.of("en_gb"), skipped);
	}

	@Test
	void languageThatCrashesIsSkippedAlone() {
		bundled.put("en_gb.rules.txt", "crash");
		assertEquals(List.of("en_us"), codes(load("en_gb", "en_us")));
		assertEquals(List.of("en_gb"), skipped);
	}

	@Test
	void noLanguageFound() {
		assertTrue(packs.load(List.of("xx_xx", "yy_yy"), (language, e) -> skipped.add(language)).isEmpty());
		assertEquals(List.of("xx_xx", "yy_yy"), skipped);
	}

	@Test
	void downloadedPackIsUsed() throws IOException {
		web.put(RELEASE, pack);
		assertFalse(packs.installed("de_de"));
		packs.download("de_de");
		assertTrue(packs.installed("de_de"));
		LanguageDictionary german = load("de_de").get(0);
		assertEquals("de_de", german.language());
		assertTrue(german.words().contains("Haus"));
		assertFalse(german.words().contains("haus"));
	}

	@Test
	void failedDownloadLeavesNothingBehind() throws IOException {
		assertThrows(IOException.class, () -> packs.download("de_de"), "offline");
		assertFalse(packs.installed("de_de"));
		assertNoFiles();
	}

	@Test
	void fileThatIsNotThePinnedOneIsRejected() throws IOException {
		web.put(RELEASE, jar("de_de.txt", "der\ndie\nHauss\n"));
		assertThrows(IOException.class, () -> packs.download("de_de"));
		web.put(RELEASE, new byte[pack.length + 1]);
		assertThrows(IOException.class, () -> packs.download("de_de"), "larger than pinned");
		assertFalse(packs.installed("de_de"));
		assertNoFiles();
	}

	private void assertNoFiles() throws IOException {
		try (var files = Files.list(dir.resolve("dictionaries"))) {
			assertEquals(List.of(), files.toList(), "no partial files are left");
		}
	}

	@Test
	void changedFileOnDiskIsNotUsed() throws IOException {
		web.put(RELEASE, pack);
		packs.download("de_de");
		byte[] tampered = jar("de_de.txt", "der\ndie\nHauss\n");
		Files.write(dir.resolve("dictionaries/de_de.jar"), tampered);
		LanguagePacks restarted = new LanguagePacks(dir.resolve("dictionaries"), packs.languages(), name -> null, (uri, maxBytes) -> {
			throw new IOException("offline");
		});
		assertFalse(restarted.installed("de_de"));
		assertTrue(restarted.load(List.of("de_de"), (language, e) -> skipped.add(language)).isEmpty());
	}

	@Test
	void partialDownloadsFromBeforeAreCleanedUp() throws IOException {
		Files.createDirectories(dir.resolve("dictionaries"));
		Files.writeString(dir.resolve("dictionaries/de_de123.part"), "cut off");
		web.put(RELEASE, pack);
		packs.download("de_de");
		try (var files = Files.list(dir.resolve("dictionaries"))) {
			assertEquals(List.of("de_de.jar"), files.map(f -> f.getFileName().toString()).toList());
		}
	}

	@Test
	void downloadingAgainKeepsTheFile() throws IOException {
		web.put(RELEASE, pack);
		packs.download("de_de");
		packs.download("de_de");
		assertEquals(List.of(RELEASE), fetched);
		assertArrayEquals(pack, Files.readAllBytes(dir.resolve("dictionaries/de_de.jar")));
	}

	@Test
	void bundledLanguagesNeedNoDownload() {
		assertTrue(packs.installed("en_gb"));
		assertThrows(IOException.class, () -> packs.download("en_gb"));
		assertTrue(load("en_gb").get(0).words().contains("colour"));
	}

	@Test
	void bundledCatalogIsValid() throws IOException {
		List<LanguagePacks.Language> languages;
		try (InputStream in = Files.newInputStream(Path.of("../common/src/main/resources/assets/bettertyping/languages.txt"))) {
			languages = LanguagePacks.readCatalog(in);
		}
		String flags = Files.readString(Path.of("../common/src/main/resources/assets/bettertyping/font/flags.json"));
		assertEquals(List.of("en_us", "en_gb"), languages.stream().filter(l -> l.pack() == null).map(LanguagePacks.Language::code).toList());
		for (LanguagePacks.Language language : languages) {
			assertTrue(Files.exists(Path.of("../common/src/main/resources/assets/bettertyping/dictionaries/" + language.code() + ".txt"))
				!= (language.pack() != null), language.code() + " is either bundled or a pack");
			String country = language.code().substring(language.code().indexOf('_') + 1);
			assertTrue(country.matches("[a-z]{2}") && flags.contains("\"file\": \"bettertyping:font/flag/" + country + ".png\", \"height\": 8, \"ascent\": 7, \"chars\": [\"\\u"
				+ HexFormat.of().toHexDigits(language.flag().charAt(0)) + "\"]"),
				language.code() + " has its flag in the flags font");
			if (language.pack() != null) {
				assertTrue(language.pack().url().toString().matches(
					"https://github\\.com/w0x7y/Better-Typing/releases/download/languages-[0-9.]+/bettertyping-" + language.code() + "-[0-9.]+\\.jar"),
					language.code() + " downloads its own jar from a languages release: " + language.pack().url());
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
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("de_de\tDeutsch\t\tlots\t" + hash + "\thttps://example.com/x.jar\n")));
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("de_de\tDeutsch\t\t10\t" + hash + "\thttps://exa mple.com/x.jar\n")));
		assertThrows(IOException.class, () -> LanguagePacks.readCatalog(stream("en_us\tEnglish\t\nen_us\tEnglish again\t\n")), "repeated code");
	}

	@Test
	void settingsCanNotNameAFile() {
		assertTrue(packs.load(List.of("../../secret"), (language, e) -> skipped.add(language)).isEmpty());
		assertEquals(List.of("../../secret"), skipped);
		assertFalse(packs.installed("../de_de"));
	}
}
