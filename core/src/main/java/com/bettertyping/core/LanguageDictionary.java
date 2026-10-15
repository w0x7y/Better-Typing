package com.bettertyping.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The language dictionary: the word list of the first of the player's languages that has one, with that
 * language's chat words moved up (see {@link WordList#language}). A language is the file
 * {@code <language>.txt} plus, if there is one, {@code <language>.chat.txt}.
 *
 * @param language its code, e.g. {@code en_us}
 */
public record LanguageDictionary(String language, WordList words) {
	/** Where dictionary files come from. */
	@FunctionalInterface
	public interface Source {
		/** Opens the named file, or returns null when there's no such file. */
		InputStream open(String name) throws IOException;
	}

	/**
	 * Loads the first of {@code languages} that has a word list. Each language passed over, because it has
	 * no word list or it couldn't be read, is reported to {@code skipped}.
	 */
	public static Optional<LanguageDictionary> loadFirst(List<String> languages, Source source, BiConsumer<String, IOException> skipped) {
		for (String language : languages) {
			try {
				return Optional.of(new LanguageDictionary(language, load(language, source)));
			} catch (IOException e) {
				skipped.accept(language, e);
			}
		}
		return Optional.empty();
	}

	private static WordList load(String language, Source source) throws IOException {
		String name = language + ".txt";
		try (InputStream words = source.open(name); InputStream chat = source.open(language + ".chat.txt")) {
			if (words == null) {
				throw new NoSuchFileException(name);
			}
			return WordList.language(words, chat != null ? chat : InputStream.nullInputStream());
		}
	}
}
