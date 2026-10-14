package com.bettertyping.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Words the player added themselves, stored one per line. Matching ignores capitalization. */
public final class PersonalDictionary {
	private static final int RANK = 2_000;

	private final Path file;
	private final Set<String> words = new TreeSet<>();
	private volatile WordList list = WordList.EMPTY;

	public PersonalDictionary(Path file) {
		this.file = file;
	}

	public synchronized void load() throws IOException {
		words.clear();
		if (file != null && Files.exists(file)) {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String word = clean(line);
				if (!word.isEmpty()) {
					words.add(word);
				}
			}
		}
		rebuild();
	}

	public boolean contains(String word) {
		return list.contains(word);
	}

	public synchronized List<String> all() {
		return List.copyOf(words);
	}

	public synchronized void add(String word) throws IOException {
		if (words.add(clean(word))) {
			save();
		}
	}

	public synchronized void remove(String word) throws IOException {
		if (words.remove(clean(word))) {
			save();
		}
	}

	WordList words() {
		return list;
	}

	private void save() throws IOException {
		rebuild();
		if (file != null) {
			Files.createDirectories(file.getParent());
			Files.write(file, words, StandardCharsets.UTF_8);
		}
	}

	private void rebuild() {
		list = WordList.caseInsensitive(words, RANK);
	}

	private static String clean(String word) {
		return Tokenizer.normalize(word.strip()).toLowerCase(Locale.ROOT);
	}
}
