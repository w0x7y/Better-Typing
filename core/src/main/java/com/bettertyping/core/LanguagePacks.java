package com.bettertyping.core;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The languages the mod can use and where their files are: bundled in its jar, or downloaded as
 * language packs.
 *
 * <p>A language is its word list {@code <code>.txt} (bundled, or in its pack), its chat words
 * {@code <code>.chat.txt} followed by the gaming slang every language shares ({@code chat.txt}), and, for
 * a language that joins words into compounds, {@code <code>.rules.txt}. Chat words and rules are always
 * the mod's own; a pack holds only the word list.
 *
 * <p>Packs come from the mod's GitHub release. The catalog ({@code languages.txt}) pins each one's URL,
 * size and SHA-512, so a download is only kept when it's exactly that file. Packs are saved as
 * {@code <code>.jar} in one folder and read straight from there. A pack a launcher installed as a mod
 * is on the classpath, so it's found like a bundled list.
 */
public final class LanguagePacks {
	private static final Pattern CODE = Pattern.compile("[a-z]{2,3}_[a-z]{2,4}");
	private static final Pattern SHA512 = Pattern.compile("[0-9a-f]{128}");
	private static final String ENTRY = "assets/bettertyping/dictionaries/";
	private static final String SHARED_CHAT_WORDS = "chat.txt";

	/**
	 * A language from the catalog.
	 *
	 * @param gameLanguages other game languages this one is used for, e.g. en_au for en_gb
	 * @param pack null when the language is bundled with the mod
	 */
	public record Language(String code, String name, List<String> gameLanguages, Pack pack) {
		/**
		 * Its flag in the mod's flags font: a private-use character for the country in its code (de_de:
		 * Germany), U+E000 + 26 * first letter + second letter. tools/make_flags.py draws the same ones.
		 */
		public String flag() {
			String country = code.substring(code.indexOf('_') + 1);
			return Character.toString(0xE000 + 26 * (country.charAt(0) - 'a') + country.charAt(1) - 'a');
		}
	}

	/** A downloadable pack: its exact size and SHA-512, and where to get it. */
	public record Pack(long size, String sha512, URI url) {
	}

	/** The mod's dictionary folder ({@code assets/bettertyping/dictionaries/}), which includes packs installed as mods. */
	@FunctionalInterface
	public interface Bundled {
		/** Opens the named file, or returns null when there's no such file. */
		InputStream open(String file) throws IOException;
	}

	/** Fetches a URL. The game uses {@link StandardHttp}; tests use their own. */
	@FunctionalInterface
	public interface Http {
		/** The response body. Fails for anything but a successful response, or a body over {@code maxBytes}. */
		InputStream get(URI uri, long maxBytes) throws IOException;
	}

	private final Path dir;
	private final Map<String, Language> catalog = new LinkedHashMap<>();
	private final Bundled bundled;
	private final Http http;
	// Whether each downloaded file matched its pinned hash, so files are hashed once per game.
	private final Map<String, Boolean> verified = new HashMap<>();
	// Whether each language is bundled (or installed as a mod); that can't change while the game runs.
	private final Map<String, Boolean> bundledLanguages = new HashMap<>();

	/**
	 * @param dir where downloaded packs are kept
	 * @param bundled the mod's own dictionary files, plus any pack installed as a mod
	 */
	public LanguagePacks(Path dir, List<Language> catalog, Bundled bundled, Http http) {
		this.dir = dir;
		for (Language language : catalog) {
			this.catalog.put(language.code(), language);
		}
		this.bundled = bundled;
		this.http = http;
	}

	/**
	 * Reads a catalog: one language per line, tab-separated: code, name, the other game languages it's
	 * used for (space-separated), and for a pack its size, SHA-512 and https URL. Blank lines and lines
	 * starting with # are skipped. Every problem, a repeated code included, is an {@link IOException}.
	 */
	public static List<Language> readCatalog(InputStream in) throws IOException {
		Map<String, Language> languages = new LinkedHashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		int number = 0;
		while ((line = reader.readLine()) != null) {
			number++;
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			Language language = catalogLine(line.split("\t", -1));
			if (language == null) {
				throw new IOException("Bad catalog line " + number + ": " + line);
			}
			if (languages.putIfAbsent(language.code(), language) != null) {
				throw new IOException("Catalog line " + number + " repeats " + language.code());
			}
		}
		return List.copyOf(languages.values());
	}

	/** One catalog line, or null when it's malformed. */
	private static Language catalogLine(String[] fields) {
		if ((fields.length != 3 && fields.length != 6) || !isCode(fields[0])) {
			return null;
		}
		List<String> gameLanguages = fields[2].isBlank() ? List.of() : List.of(fields[2].strip().split(" +"));
		if (fields.length == 3) {
			return new Language(fields[0], fields[1], gameLanguages, null);
		}
		try {
			URI url = URI.create(fields[5].strip());
			long size = Long.parseLong(fields[3]);
			if (!"https".equals(url.getScheme()) || !SHA512.matcher(fields[4]).matches() || size <= 0) {
				return null;
			}
			return new Language(fields[0], fields[1], gameLanguages, new Pack(size, fields[4], url));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Whether {@code code} looks like a language code (en_us, pt_br), so it's safe in a file name. */
	public static boolean isCode(String code) {
		return CODE.matcher(code).matches();
	}

	/** Every language in the catalog, in catalog order. */
	public List<Language> languages() {
		return List.copyOf(catalog.values());
	}

	/** The catalog's language, or null. */
	public Language language(String code) {
		return catalog.get(code);
	}

	/** Whether the language's words are here: bundled, installed as a mod, or downloaded. Cheap after the first call. */
	public synchronized boolean installed(String code) {
		if (!isCode(code)) {
			return false;
		}
		boolean inJar = bundledLanguages.computeIfAbsent(code, c -> {
			try (InputStream in = bundled.open(c + ".txt")) {
				return in != null;
			} catch (IOException e) {
				return false;
			}
		});
		return inJar || downloaded(code);
	}

	/**
	 * Loads each of {@code languages} that has a word list. Each language passed over, because it isn't a
	 * language code, has no word list or couldn't be read, is reported to {@code skipped}. Reads files;
	 * call it off the game thread.
	 */
	public List<LanguageDictionary> load(List<String> languages, BiConsumer<String, IOException> skipped) {
		List<LanguageDictionary> dictionaries = new ArrayList<>();
		for (String language : languages) {
			if (!isCode(language)) {
				skipped.accept(language, new IOException("Not a language code"));
				continue;
			}
			try {
				dictionaries.add(new LanguageDictionary(language, loadWords(language)));
			} catch (IOException e) {
				skipped.accept(language, e);
			} catch (RuntimeException e) {
				skipped.accept(language, new IOException("Loading failed", e));
			}
		}
		return dictionaries;
	}

	private WordList loadWords(String code) throws IOException {
		try (InputStream words = wordList(code); InputStream chat = chatWords(code)) {
			if (words == null) {
				throw new NoSuchFileException(code + ".txt");
			}
			return WordList.language(words, chat, compounds(code));
		}
	}

	/** The word list, bundled or from the downloaded pack, or null. */
	private InputStream wordList(String code) throws IOException {
		InputStream bundledList = bundled.open(code + ".txt");
		if (bundledList != null || !catalog.containsKey(code) || !downloaded(code)) {
			return bundledList;
		}
		ZipFile zip = new ZipFile(file(code).toFile());
		try {
			ZipEntry entry = zip.getEntry(ENTRY + code + ".txt");
			if (entry == null) {
				throw new NoSuchFileException(file(code) + "!/" + ENTRY + code + ".txt");
			}
			return new FilterInputStream(zip.getInputStream(entry)) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						zip.close();
					}
				}
			};
		} catch (IOException e) {
			zip.close();
			throw e;
		}
	}

	/** The language's own chat words, then the shared ones. */
	private InputStream chatWords(String code) throws IOException {
		InputStream own = orEmpty(bundled.open(code + ".chat.txt"));
		try {
			InputStream shared = orEmpty(bundled.open(SHARED_CHAT_WORDS));
			return new SequenceInputStream(own, new SequenceInputStream(new ByteArrayInputStream(new byte[] {'\n'}), shared));
		} catch (IOException e) {
			own.close();
			throw e;
		}
	}

	private WordList.Compounds compounds(String code) throws IOException {
		try (InputStream rules = bundled.open(code + ".rules.txt")) {
			return rules == null ? WordList.Compounds.NONE : WordList.Compounds.read(rules);
		}
	}

	private static InputStream orEmpty(InputStream in) {
		return in != null ? in : InputStream.nullInputStream();
	}

	/**
	 * Downloads a language's pack unless it's already here. Only the pinned file is kept, replacing any
	 * older pack. Blocks; call it off the game thread, and not twice at once for one language.
	 */
	public void download(String code) throws IOException {
		Language language = catalog.get(code);
		if (language == null || language.pack() == null) {
			throw new IOException("No pack to download for " + code);
		}
		if (installed(code)) {
			return;
		}
		Pack pack = language.pack();
		Files.createDirectories(dir);
		deleteLeftovers(code);
		Path part = Files.createTempFile(dir, code, ".part");
		try {
			String sha512;
			try (InputStream body = http.get(pack.url(), pack.size()); OutputStream out = Files.newOutputStream(part)) {
				sha512 = copyAndHash(body, out, pack.size());
			}
			if (Files.size(part) != pack.size() || !sha512.equals(pack.sha512())) {
				throw new IOException(pack.url() + " gave a different file than the catalog pins");
			}
			Files.move(part, file(code), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			synchronized (this) {
				verified.put(code, true);
			}
		} finally {
			Files.deleteIfExists(part);
		}
	}

	/** Partial downloads a closed game left behind. */
	private void deleteLeftovers(String code) throws IOException {
		try (DirectoryStream<Path> parts = Files.newDirectoryStream(dir, code + "*.part")) {
			for (Path part : parts) {
				Files.deleteIfExists(part);
			}
		}
	}

	/** Whether the downloaded file for {@code code} is there and is the pinned one. */
	private synchronized boolean downloaded(String code) {
		Language language = catalog.get(code);
		if (language == null || language.pack() == null) {
			return false;
		}
		return verified.computeIfAbsent(code, c -> {
			Path file = file(c);
			try (InputStream in = Files.newInputStream(file)) {
				return Files.size(file) == language.pack().size()
					&& copyAndHash(in, OutputStream.nullOutputStream(), language.pack().size()).equals(language.pack().sha512());
			} catch (IOException e) {
				return false;
			}
		});
	}

	private Path file(String code) {
		return dir.resolve(code + ".jar");
	}

	/** Copies at most {@code limit} bytes, failing past that, and returns the SHA-512 in hex. */
	private static String copyAndHash(InputStream in, OutputStream out, long limit) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		byte[] buffer = new byte[64 * 1024];
		long total = 0;
		try (DigestInputStream hashing = new DigestInputStream(in, digest)) {
			int read;
			while ((read = hashing.read(buffer)) != -1) {
				total += read;
				if (total > limit) {
					throw new IOException("Larger than the expected " + limit + " bytes");
				}
				out.write(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
