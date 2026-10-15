package com.bettertyping.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
import java.util.Properties;
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
 * word by itself, but a word of the list with it in front counts as correct ("l'homme"). A language
 * with {@link Compounds} also accepts words joined into one (German "Spielplatz").
 */
public final class WordList {
	public static final WordList EMPTY = new WordList(Map.of(), false, Compounds.NONE);
	static final int CHAT_SPACING = 3;

	/**
	 * How a language joins words into one, read from its rules file: {@code compound.min_part} (the
	 * shortest part; compounds are off without it), {@code compound.links} (letters allowed between parts,
	 * as in Arbeit-s-zimmer), {@code compound.stems} (endings a part may drop, as in Wasch(en)maschine)
	 * and {@code compound.skip_most_common} (how many of the most common words, like "der" and "und",
	 * can't be parts, so two words typed without a space stay misspelled). Lists are space-separated.
	 */
	public record Compounds(int minPart, List<String> links, List<String> stemEndings, int skipMostCommon) {
		public static final Compounds NONE = new Compounds(0, List.of(), List.of(), 0);

		public static Compounds read(InputStream in) throws IOException {
			Properties rules = new Properties();
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				rules.load(reader);
			}
			try {
				return new Compounds(Integer.parseInt(rules.getProperty("compound.min_part", "0").strip()),
					list(rules, "compound.links"), list(rules, "compound.stems"),
					Integer.parseInt(rules.getProperty("compound.skip_most_common", "0").strip()));
			} catch (NumberFormatException e) {
				throw new IOException("Bad number in compound rules", e);
			}
		}

		private static List<String> list(Properties rules, String key) {
			String value = rules.getProperty(key, "").strip();
			return value.isEmpty() ? List.of() : List.of(value.toLowerCase(Locale.ROOT).split("\\s+"));
		}

		boolean enabled() {
			return minPart > 0;
		}
	}

	private final boolean caseSensitive;
	private final Map<String, Integer> exact;
	private final Map<String, Integer> lowerRank = new HashMap<>();
	private final Map<String, String> lowerDisplay = new HashMap<>();
	private final Set<String> elisions = new HashSet<>();
	private final Compounds compounds;
	private final String[] sortedLower;
	private final List<List<String>> byLength = new ArrayList<>();

	private WordList(Map<String, Integer> entries, boolean caseSensitive, Compounds compounds) {
		this.compounds = compounds;
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
		return new WordList(readRanked(in), true, Compounds.NONE);
	}

	/**
	 * A language dictionary (ranked lines) with the words of {@code chatWords} moved up. The dictionary
	 * is ranked by how often words appear in books, which rarely say "hello" or "thanks", so the n-th
	 * chat word ranks at least as high as the dictionary's (n * {@value #CHAT_SPACING})-th word. Chat words
	 * the dictionary lacks ("gg", "afk") count as correct. Blank lines and {@code #} comments are skipped.
	 */
	public static WordList language(InputStream dictionary, InputStream chatWords) throws IOException {
		return language(dictionary, chatWords, Compounds.NONE);
	}

	/** {@link #language(InputStream, InputStream)} for a language that joins words into compounds. */
	public static WordList language(InputStream dictionary, InputStream chatWords, Compounds compounds) throws IOException {
		Map<String, Integer> ranks = readRanked(dictionary);
		int position = 0;
		for (String word : readLines(chatWords)) {
			if (!word.startsWith("#")) {
				ranks.merge(word, position++ * CHAT_SPACING, Math::min);
			}
		}
		return new WordList(ranks, true, compounds);
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
		return new WordList(ranks, true, Compounds.NONE);
	}

	/** A case-insensitive list where every word gets the same rank. */
	public static WordList caseInsensitive(Collection<String> words, int rank) {
		Map<String, Integer> ranks = new HashMap<>();
		for (String word : words) {
			ranks.putIfAbsent(Tokenizer.normalize(word).toLowerCase(Locale.ROOT), rank);
		}
		return new WordList(ranks, false, Compounds.NONE);
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
		if (elided > 0 && contains(word.substring(elided))) {
			return true;
		}
		return compounds.enabled() && isCompound(word);
	}

	/**
	 * Whether the word is two or more of the list's words joined, with the allowed links between them.
	 * The last part takes the whole word's capitalization: German "Spielplatz" ends in the noun "Platz",
	 * so "spielplatz" stays misspelled.
	 */
	private boolean isCompound(String word) {
		CaseStyle style = CaseStyle.of(word);
		String lower = word.toLowerCase(Locale.ROOT);
		int length = lower.length();
		if (style == CaseStyle.MIXED || length < 2 * compounds.minPart()) {
			return false;
		}
		boolean[] partStarts = new boolean[length];
		partStarts[0] = true;
		for (int start = 0; start < length; start++) {
			if (!partStarts[start]) {
				continue;
			}
			for (int end = start + compounds.minPart(); end <= length; end++) {
				String part = lower.substring(start, end);
				if (end == length) {
					if (start > 0 && isPart(part) && containsWord(style.apply(part))) {
						return true;
					}
				} else if (isPart(part) || compounds.stemEndings().stream().anyMatch(ending -> isPart(part + ending))) {
					markNextParts(lower, end, partStarts);
				}
			}
		}
		return false;
	}

	/** After a part ending at {@code end}, the next one can start right there or after a link. */
	private void markNextParts(String lower, int end, boolean[] partStarts) {
		if (end < partStarts.length) {
			partStarts[end] = true;
		}
		for (String link : compounds.links()) {
			int next = end + link.length();
			if (next < partStarts.length && lower.startsWith(link, end)) {
				partStarts[next] = true;
			}
		}
	}

	private boolean isPart(String lower) {
		Integer rank = lowerRank.get(lower);
		return rank != null && rank >= compounds.skipMostCommon();
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
