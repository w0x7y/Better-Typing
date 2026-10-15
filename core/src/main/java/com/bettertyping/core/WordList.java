package com.bettertyping.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * An immutable set of words with a popularity rank (lower = more common).
 *
 * <p>Case-sensitive lists follow spell-checker conventions: "London" is only correct capitalized,
 * while "hello" is also correct as "Hello" (sentence start) and "HELLO". Case-insensitive lists
 * (game names, the personal dictionary) accept any capitalization.
 *
 * <p>An entry ending in an apostrophe is an elision ("l'" in French, "dell'" in Italian): it isn't a
 * word by itself, but a word of the list with it in front counts as correct ("l'homme").
 */
public final class WordList {
	public static final WordList EMPTY = new WordList(Map.of(), false);
	static final int CHAT_SPACING = 3;

	private final boolean caseSensitive;
	private final Map<String, Integer> exact;
	private final Map<String, Integer> lowerRank = new HashMap<>();
	private final Map<String, String> lowerDisplay = new HashMap<>();
	private final Set<String> elisions = new HashSet<>();
	private final String[] sortedLower;
	private final List<List<String>> byLength = new ArrayList<>();

	private WordList(Map<String, Integer> entries, boolean caseSensitive) {
		Map<String, Integer> ranks = new HashMap<>();
		for (Map.Entry<String, Integer> entry : entries.entrySet()) {
			String word = entry.getKey();
			if (word.length() > 1 && word.endsWith("'")) {
				elisions.add(word.toLowerCase(Locale.ROOT));
			} else {
				ranks.put(word, entry.getValue());
			}
		}
		this.caseSensitive = caseSensitive;
		this.exact = caseSensitive ? Map.copyOf(ranks) : Map.of();
		for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
			String word = entry.getKey();
			String lower = word.toLowerCase(Locale.ROOT);
			int rank = entry.getValue();
			Integer best = lowerRank.get(lower);
			if (best == null || rank < best) {
				lowerRank.put(lower, rank);
			}
			// Prefer the all-lowercase form for display ("in" over "IN"), otherwise the most common variant.
			String shown = lowerDisplay.get(lower);
			if (shown == null || word.equals(lower) || (!shown.equals(lower) && rank < ranks.getOrDefault(shown, Integer.MAX_VALUE))) {
				lowerDisplay.put(lower, caseSensitive ? word : lower);
			}
		}
		sortedLower = lowerRank.keySet().toArray(String[]::new);
		Arrays.sort(sortedLower);
		for (String lower : sortedLower) {
			while (byLength.size() <= lower.length()) {
				byLength.add(new ArrayList<>());
			}
			byLength.get(lower.length()).add(lower);
		}
	}

	/** A case-sensitive list where each line is a word and the line number is its rank. */
	public static WordList fromRankedLines(InputStream in) throws IOException {
		return new WordList(readRanked(in), true);
	}

	/**
	 * A language dictionary (ranked lines) with the words of {@code chatWords} moved up. The dictionary
	 * is ranked by how often words appear in books, which rarely say "hello" or "thanks", so the n-th
	 * chat word ranks at least as high as the dictionary's (n * {@value #CHAT_SPACING})-th word. Chat words
	 * the dictionary lacks ("gg", "afk") count as correct. Blank lines and {@code #} comments are skipped.
	 */
	public static WordList language(InputStream dictionary, InputStream chatWords) throws IOException {
		Map<String, Integer> ranks = readRanked(dictionary);
		int position = 0;
		for (String word : readLines(chatWords)) {
			if (!word.startsWith("#")) {
				ranks.merge(word, position++ * CHAT_SPACING, Math::min);
			}
		}
		return new WordList(ranks, true);
	}

	private static Map<String, Integer> readRanked(InputStream in) throws IOException {
		Map<String, Integer> ranks = new HashMap<>();
		for (String word : readLines(in)) {
			ranks.put(word, ranks.size());
		}
		return ranks;
	}

	/** The non-blank lines, normalized, without duplicates. */
	private static Collection<String> readLines(InputStream in) throws IOException {
		Collection<String> lines = new LinkedHashSet<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String word = Tokenizer.normalize(line.strip());
				if (!word.isEmpty()) {
					lines.add(word);
				}
			}
		}
		return lines;
	}

	public static WordList caseSensitive(Map<String, Integer> ranks) {
		return new WordList(ranks, true);
	}

	/** A case-insensitive list where every word gets the same rank. */
	public static WordList caseInsensitive(Collection<String> words, int rank) {
		Map<String, Integer> ranks = new HashMap<>();
		for (String word : words) {
			ranks.putIfAbsent(Tokenizer.normalize(word).toLowerCase(Locale.ROOT), rank);
		}
		return new WordList(ranks, false);
	}

	public int size() {
		return sortedLower.length;
	}

	public boolean contains(String word) {
		word = Tokenizer.normalize(word);
		if (containsWord(word)) {
			return true;
		}
		int elided = elisionEnd(word);
		return elided > 0 && contains(word.substring(elided));
	}

	private boolean containsWord(String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		if (!caseSensitive) {
			return lowerRank.containsKey(lower);
		}
		if (exact.containsKey(word)) {
			return true;
		}
		return switch (CaseStyle.of(word)) {
			case CAPITALIZED -> exact.containsKey(lower);
			case UPPER -> lowerRank.containsKey(lower);
			case LOWER, MIXED -> false;
		};
	}

	/**
	 * Where the word after a leading elision starts ("l'homme" gives 2), or -1 when the word doesn't
	 * start with one of the list's elisions.
	 */
	private int elisionEnd(String word) {
		int apostrophe = word.indexOf('\'');
		if (apostrophe < 0 || apostrophe == word.length() - 1 || elisions.isEmpty()) {
			return -1;
		}
		return elisions.contains(word.substring(0, apostrophe + 1).toLowerCase(Locale.ROOT)) ? apostrophe + 1 : -1;
	}

	/** Runs {@code collect} on the word after a leading elision and puts the elision back in front, as typed. */
	private boolean afterElision(String word, List<Candidate> out, BiConsumer<String, List<Candidate>> collect) {
		word = Tokenizer.normalize(word);
		int elided = elisionEnd(word);
		if (elided < 0) {
			return false;
		}
		List<Candidate> rest = new ArrayList<>();
		collect.accept(word.substring(elided), rest);
		for (Candidate candidate : rest) {
			out.add(new Candidate(word.substring(0, elided) + candidate.word(), candidate.distance(), candidate.rank()));
		}
		return true;
	}

	/** Adds candidates for spelling suggestions: words within {@code maxDistance} edits of {@code word}. */
	void collectSuggestions(String word, int maxDistance, List<Candidate> out) {
		if (afterElision(word, out, (rest, candidates) -> collectSuggestions(rest, maxDistance, candidates))) {
			return;
		}
		String lower = Tokenizer.normalize(word).toLowerCase(Locale.ROOT);
		boolean allowPossessive = lower.indexOf('\'') >= 0;
		int from = Math.max(1, lower.length() - maxDistance);
		int to = Math.min(byLength.size() - 1, lower.length() + maxDistance);
		for (int length = from; length <= to; length++) {
			for (String key : byLength.get(length)) {
				if (!allowPossessive && key.indexOf('\'') >= 0) {
					continue;
				}
				int distance = EditDistance.bounded(lower, key, maxDistance);
				if (distance <= maxDistance) {
					out.add(new Candidate(lowerDisplay.get(key), distance, lowerRank.get(key)));
				}
			}
		}
	}

	/** Adds autocomplete candidates: words starting with {@code prefix}, excluding the prefix itself. */
	void collectCompletions(String prefix, List<Candidate> out) {
		if (afterElision(prefix, out, this::collectCompletions)) {
			return;
		}
		String lower = Tokenizer.normalize(prefix).toLowerCase(Locale.ROOT);
		boolean allowPossessive = lower.indexOf('\'') >= 0;
		int i = Arrays.binarySearch(sortedLower, lower);
		if (i < 0) {
			i = -i - 1;
		}
		for (; i < sortedLower.length && sortedLower[i].startsWith(lower); i++) {
			String key = sortedLower[i];
			if (key.equals(lower) || (!allowPossessive && key.indexOf('\'') >= 0)) {
				continue;
			}
			out.add(new Candidate(lowerDisplay.get(key), 0, lowerRank.get(key)));
		}
	}

	/** A suggested word with its edit distance from the input and its popularity rank. */
	record Candidate(String word, int distance, int rank) {
	}
}
