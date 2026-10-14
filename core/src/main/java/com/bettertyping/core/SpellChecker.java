package com.bettertyping.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks words against the language dictionary, game names (items, mobs) and the personal dictionary.
 * All sources can be swapped at any time from another thread, e.g. when a dictionary finishes loading.
 */
public final class SpellChecker {
	private static final int MAX_DISTANCE = 2;
	private static final Comparator<WordList.Candidate> BY_DISTANCE_THEN_RANK =
		Comparator.comparingInt(WordList.Candidate::distance).thenComparingInt(WordList.Candidate::rank);
	private static final Comparator<WordList.Candidate> BY_RANK = Comparator.comparingInt(WordList.Candidate::rank);

	private final PersonalDictionary personal;
	private volatile WordList language;
	private volatile WordList gameWords = WordList.EMPTY;
	private volatile int version;

	public SpellChecker(PersonalDictionary personal) {
		this.personal = personal;
	}

	public PersonalDictionary personal() {
		return personal;
	}

	/** Null while the dictionary is still loading; nothing is flagged until it's ready. */
	public void setLanguage(WordList language) {
		this.language = language;
		version++;
	}

	public boolean isReady() {
		return language != null;
	}

	public void setGameWords(WordList gameWords) {
		this.gameWords = gameWords;
		version++;
	}

	public void addPersonalWord(String word) throws IOException {
		personal.add(word);
		version++;
	}

	public void removePersonalWord(String word) throws IOException {
		personal.remove(word);
		version++;
	}

	/** Changes whenever any word source changes, so cached results can be refreshed. */
	public int version() {
		return version;
	}

	public boolean isCorrect(String word) {
		WordList lang = language;
		return lang == null || lang.contains(word) || gameWords.contains(word) || personal.contains(word);
	}

	/** Up to {@code limit} corrections for a misspelled word, closest and most common first. */
	public List<String> suggest(String word, int limit) {
		List<WordList.Candidate> candidates = new ArrayList<>();
		for (WordList list : sources()) {
			list.collectSuggestions(word, MAX_DISTANCE, candidates);
		}
		candidates.sort(BY_DISTANCE_THEN_RANK);
		return top(candidates, word, limit);
	}

	/** Up to {@code limit} words that start with {@code prefix}, most common first. */
	public List<String> complete(String prefix, int limit) {
		List<WordList.Candidate> candidates = new ArrayList<>();
		for (WordList list : sources()) {
			list.collectCompletions(prefix, candidates);
		}
		candidates.sort(BY_RANK);
		return top(candidates, prefix, limit);
	}

	private List<WordList> sources() {
		List<WordList> lists = new ArrayList<>(3);
		WordList lang = language;
		if (lang != null) {
			lists.add(lang);
		}
		lists.add(gameWords);
		lists.add(personal.words());
		return lists;
	}

	private static List<String> top(List<WordList.Candidate> sorted, String typed, int limit) {
		CaseStyle style = CaseStyle.of(typed);
		Set<String> result = new LinkedHashSet<>();
		for (WordList.Candidate candidate : sorted) {
			if (result.size() >= limit) {
				break;
			}
			String word = style.apply(candidate.word());
			if (!word.equals(typed)) {
				result.add(word);
			}
		}
		return List.copyOf(result);
	}
}
