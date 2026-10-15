package com.bettertyping.core;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The languages the mod can use: bundled in its jar, or downloaded as language packs.
 *
 * <p>The catalog ({@code languages.txt}) pins each pack's size and SHA-512, so a download is only kept
 * when it's exactly that file, whichever mirror it came from. Packs are saved as
 * {@code <code>.jar} in one folder and read straight from there. A pack a launcher installed as a mod
 * is on the classpath, so the bundled source finds it.
 */
public final class LanguagePacks {
	/** The setting for "use the game's language". */
	public static final String GAME_LANGUAGE = "auto";
	/** Always tried last, so there's a dictionary whatever is configured. */
	public static final String FALLBACK = "en_us";
	private static final Pattern SHA512 = Pattern.compile("[0-9a-f]{128}");
	private static final String ENTRY = "assets/bettertyping/dictionaries/";

	/**
	 * A language from the catalog.
	 *
	 * @param gameLanguages other game languages this one is used for, e.g. en_au for en_gb
	 * @param pack null when the language is bundled with the mod
	 */
	public record Language(String code, String name, List<String> gameLanguages, Pack pack) {
	}

	/** A downloadable pack: its exact size and SHA-512, and where to get it, tried in order. */
	public record Pack(long size, String sha512, List<URI> urls) {
	}

	/** Fetches a URL. Implemented over {@link HttpClient} by {@link #standardHttp}; tests use their own. */
	@FunctionalInterface
	public interface Http {
		/** The response body. Fails for anything but a successful response. */
		InputStream get(URI uri) throws IOException;
	}

	private final Path dir;
	private final Map<String, Language> catalog = new LinkedHashMap<>();
	private final LanguageDictionary.Source bundled;
	private final Http http;
	// Whether each downloaded file matched its pinned hash, so files are hashed once per game.
	private final Map<String, Boolean> verified = new HashMap<>();

	/**
	 * @param dir where downloaded packs are kept
	 * @param bundled the mod's own word lists, plus any pack installed as a mod
	 */
	public LanguagePacks(Path dir, List<Language> catalog, LanguageDictionary.Source bundled, Http http) {
		this.dir = dir;
		for (Language language : catalog) {
			this.catalog.put(language.code(), language);
		}
		this.bundled = bundled;
		this.http = http;
	}

	/**
	 * Reads a catalog: one language per line, tab-separated: code, name, the other game languages it's
	 * used for (space-separated), and for a pack its size, SHA-512 and https URLs (space-separated).
	 * Blank lines and lines starting with # are skipped.
	 */
	public static List<Language> readCatalog(InputStream in) throws IOException {
		List<Language> languages = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		int number = 0;
		while ((line = reader.readLine()) != null) {
			number++;
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			String[] fields = line.split("\t", -1);
			if ((fields.length != 3 && fields.length != 6) || !LanguageDictionary.isCode(fields[0])) {
				throw new IOException("Bad catalog line " + number + ": " + line);
			}
			List<String> gameLanguages = fields[2].isBlank() ? List.of() : List.of(fields[2].strip().split(" +"));
			Pack pack = null;
			if (fields.length == 6) {
				List<URI> urls = new ArrayList<>();
				for (String url : fields[5].strip().split(" +")) {
					URI uri = URI.create(url);
					if (!"https".equals(uri.getScheme())) {
						throw new IOException("Catalog line " + number + " has a URL that isn't https: " + url);
					}
					urls.add(uri);
				}
				if (!SHA512.matcher(fields[4]).matches()) {
					throw new IOException("Bad SHA-512 on catalog line " + number);
				}
				pack = new Pack(Long.parseLong(fields[3]), fields[4], List.copyOf(urls));
			}
			languages.add(new Language(fields[0], fields[1], gameLanguages, pack));
		}
		return languages;
	}

	/** An {@link Http} over the JDK's client, with short timeouts. The client starts on the first download. */
	public static Http standardHttp(String userAgent) {
		return new Http() {
			private HttpClient client;

			@Override
			public InputStream get(URI uri) throws IOException {
				return send(client(), uri, userAgent);
			}

			private synchronized HttpClient client() {
				if (client == null) {
					client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(10))
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
				}
				return client;
			}
		};
	}

	private static InputStream send(HttpClient client, URI uri, String userAgent) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent", userAgent).build();
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("HTTP " + response.statusCode() + " from " + uri);
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while fetching " + uri, e);
		}
	}

	/** Every language in the catalog, in catalog order. */
	public List<Language> languages() {
		return List.copyOf(catalog.values());
	}

	/** The catalog's language, or null. */
	public Language language(String code) {
		return catalog.get(code);
	}

	/** The catalog language used for a game language (de_de, or en_gb for en_au), or null. */
	public Language forGame(String gameLanguage) {
		String code = gameLanguage.toLowerCase(Locale.ROOT);
		Language exact = catalog.get(code);
		if (exact != null) {
			return exact;
		}
		for (Language language : catalog.values()) {
			if (language.gameLanguages().contains(code)) {
				return language;
			}
		}
		return null;
	}

	/** Whether the language's words are here: bundled, installed as a mod, or downloaded. */
	public synchronized boolean installed(String code) {
		if (!LanguageDictionary.isCode(code)) {
			return false;
		}
		try (InputStream in = bundled.open(code + ".txt")) {
			if (in != null) {
				return true;
			}
		} catch (IOException e) {
			return false;
		}
		return downloaded(code);
	}

	/**
	 * The installed languages to try, in order, for the configured ones: {@link #GAME_LANGUAGE} stands for
	 * the game language's catalog entry, and {@link #FALLBACK} comes last. A language that isn't
	 * installed (yet) is left out, so the list changes once its download finishes.
	 */
	public List<String> resolve(List<String> configured, String gameLanguage) {
		List<String> codes = new ArrayList<>();
		for (String code : configured) {
			if (code.equals(GAME_LANGUAGE)) {
				Language language = forGame(gameLanguage);
				code = language != null ? language.code() : null;
			}
			if (code != null && !codes.contains(code) && installed(code)) {
				codes.add(code);
			}
		}
		if (!codes.contains(FALLBACK)) {
			codes.add(FALLBACK);
		}
		return codes;
	}

	/**
	 * Downloads a language's pack unless it's already here. Tries each URL until one gives exactly the
	 * pinned file, which then replaces any older pack. Blocks; call it off the game thread.
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
		IOException failures = new IOException("Couldn't download the " + code + " pack");
		for (URI url : pack.urls()) {
			Path part = Files.createTempFile(dir, code, ".part");
			try {
				String sha512;
				try (InputStream body = http.get(url); OutputStream out = Files.newOutputStream(part)) {
					sha512 = copyAndHash(body, out, pack.size());
				}
				if (Files.size(part) != pack.size() || !sha512.equals(pack.sha512())) {
					throw new IOException(url + " gave a different file than the catalog pins");
				}
				Files.move(part, file(code), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				synchronized (this) {
					verified.put(code, true);
				}
				return;
			} catch (IOException e) {
				failures.addSuppressed(e);
			} finally {
				Files.deleteIfExists(part);
			}
		}
		throw failures;
	}

	/** Where dictionary files come from: the bundled ones first, then downloaded packs. */
	public LanguageDictionary.Source source() {
		return name -> {
			InputStream in = bundled.open(name);
			return in != null ? in : openDownloaded(name);
		};
	}

	/** A file from a downloaded pack, if the pack matches the catalog. Packs hold no chat words. */
	private InputStream openDownloaded(String name) throws IOException {
		if (!name.endsWith(".txt") || name.endsWith(".chat.txt")) {
			return null;
		}
		String code = name.substring(0, name.length() - ".txt".length());
		if (!catalog.containsKey(code) || !downloaded(code)) {
			return null;
		}
		ZipFile zip = new ZipFile(file(code).toFile());
		ZipEntry entry = zip.getEntry(ENTRY + name);
		if (entry == null) {
			zip.close();
			throw new NoSuchFileException(file(code) + "!/" + ENTRY + name);
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
