package com.bettertyping.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * The language dictionary: the word list of the first of the player's languages that has one, with that
 * language's chat words moved up (see {@link WordList#language}). A language is the file
 * {@code <language>.txt} plus its chat words: {@code <language>.chat.txt}, or for a language without
 * its own, the shared gaming slang in {@code chat.txt} (gg, afk).
 *
 * @param language its code, e.g. {@code en_us}
 */
public record LanguageDictionary(String language, WordList words) {
	private static final Pattern CODE = Pattern.compile("[a-z]{2,3}_[a-z]{2,4}");

	/** Where dictionary files come from. */
	@FunctionalInterface
	public interface Source {
		/** Opens the named file, or returns null when there's no such file. */
		InputStream open(String name) throws IOException;
	}

	/**
	 * Loads the first of {@code languages} that has a word list. Each language passed over, because it
	 * isn't a language code, has no word list or couldn't be read, is reported to {@code skipped}.
	 */
	public static Optional<LanguageDictionary> loadFirst(List<String> languages, Source source, BiConsumer<String, IOException> skipped) {
		for (String language : languages) {
			if (!isCode(language)) {
				skipped.accept(language, new IOException("Not a language code"));
				continue;
			}
			try {
				return Optional.of(new LanguageDictionary(language, load(language, source)));
			} catch (IOException e) {
				skipped.accept(language, e);
			}
		}
		return Optional.empty();
	}

	/** Whether {@code code} looks like a language code (en_us, pt_br), so it's safe in a file name. */
	public static boolean isCode(String code) {
		return CODE.matcher(code).matches();
	}

	private static WordList load(String language, Source source) throws IOException {
		String name = language + ".txt";
		try (InputStream words = source.open(name); InputStream chat = chatWords(language, source)) {
			if (words == null) {
				throw new NoSuchFileException(name);
			}
			return WordList.language(words, chat != null ? chat : InputStream.nullInputStream());
		}
	}

	private static InputStream chatWords(String language, Source source) throws IOException {
		InputStream own = source.open(language + ".chat.txt");
		return own != null ? own : source.open("chat.txt");
	}
}
