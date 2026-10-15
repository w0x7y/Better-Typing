package com.bettertyping.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Words the player added themselves, stored one per line. Matching ignores capitalization. Only used
 * through {@link SpellChecker}, which keeps its word sources in step and guards it with its lock.
 */
final class PersonalDictionary {
	private static final int RANK = 2_000;

	private final Path file;
	private final Consumer<IOException> errors;
	private final Set<String> words = new TreeSet<>();
	// False when the file exists but couldn't be read: it's left alone rather than overwritten with only the new words.
	private boolean writable;
	private WordList list = WordList.EMPTY;

	/** Reads the file, if there is one. Read and write failures go to {@code errors}. */
	PersonalDictionary(Path file, Consumer<IOException> errors) {
		this.file = file;
		this.errors = errors;
		try {
			if (Files.exists(file)) {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					addClean(line);
				}
			}
			writable = true;
		} catch (IOException e) {
			words.clear();
			errors.accept(new IOException("Couldn't read " + file, e));
		}
		list = WordList.caseInsensitive(words, RANK);
	}

	WordList words() {
		return list;
	}

	/** Lowercase and sorted. */
	List<String> all() {
		return List.copyOf(words);
	}

	void add(String word) {
		if (addClean(word)) {
			changed();
		}
	}

	/** Replaces every word. Each entry can hold several words separated by spaces. */
	void set(Collection<String> entries) {
		Set<String> old = new TreeSet<>(words);
		words.clear();
		for (String entry : entries) {
			for (String word : entry.strip().split("\\s+")) {
				addClean(word);
			}
		}
		if (!words.equals(old)) {
			changed();
		}
	}

	private boolean addClean(String word) {
		String clean = Tokenizer.normalize(word.strip()).toLowerCase(Locale.ROOT);
		return !clean.isEmpty() && words.add(clean);
	}

	/** The new words count right away, even if saving them fails. */
	private void changed() {
		list = WordList.caseInsensitive(words, RANK);
		if (!writable) {
			errors.accept(new IOException("Not saving " + file + ", since it couldn't be read"));
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, words, StandardCharsets.UTF_8);
		} catch (IOException e) {
			errors.accept(new IOException("Couldn't save " + file, e));
		}
	}
}
