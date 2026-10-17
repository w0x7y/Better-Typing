package com.bettertyping.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Checks words against the language dictionaries, game names (items, mobs) and the personal dictionary.
 * A word is correct when any of them has it.
 *
 * <p>Any source can be replaced at any time from any thread, e.g. when the dictionaries finish loading.
 * Until languages are set, every word counts as correct and nothing is reported as misspelled.
 */
public final class SpellChecker {
	private static final int MAX_DISTANCE = 2;
	private static final int CACHED_TEXTS = 32;
	private static final Comparator<WordList.Candidate> BY_DISTANCE_THEN_RANK =
		Comparator.comparingInt(WordList.Candidate::distance).thenComparingInt(WordList.Candidate::rank);
	private static final Comparator<WordList.Candidate> BY_RANK = Comparator.comparingInt(WordList.Candidate::rank);

	/** Every word source in use. Replaced as a whole when one changes, so readers always see a consistent set. */
	private record Sources(List<WordList> languages, WordList gameWords, WordList personal) {
		boolean isCorrect(String word) {
			return languages == null || lists().stream().anyMatch(list -> list.contains(word));
		}

		List<WordList> lists() {
			List<WordList> lists = new ArrayList<>(languages == null ? List.of() : languages);
			lists.add(gameWords);
			lists.add(personal);
			return lists;
		}
	}

	private final PersonalDictionary personal;
	private volatile Sources sources;
	/** Misspelled words per text, valid for {@link #cachedFor} only. */
	private final Map<String, List<WordSpan>> misspelledCache = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<WordSpan>> eldest) {
			return size() > CACHED_TEXTS;
		}
	};
	private Sources cachedFor;

	/**
	 * @param personalFile where the personal dictionary is kept: read now, written after every change
	 * @param personalFileErrors told when that file can't be read or saved; added words still count until the game closes
	 */
	public SpellChecker(Path personalFile, Consumer<IOException> personalFileErrors) {
		this.personal = new PersonalDictionary(personalFile, personalFileErrors);
		this.sources = new Sources(null, WordList.EMPTY, personal.words());
	}

	public void setLanguages(List<WordList> languages) {
		List<WordList> copy = List.copyOf(languages);
		update(s -> new Sources(copy, s.gameWords(), s.personal()));
	}

	public void setGameWords(WordList gameWords) {
		update(s -> new Sources(s.languages(), gameWords, s.personal()));
	}

	/** Adds a word to the personal dictionary. */
	public synchronized void addPersonalWord(String word) {
		personal.add(word);
		update(s -> new Sources(s.languages(), s.gameWords(), personal.words()));
	}

	/** Replaces the personal dictionary's words. Each entry can hold several words separated by spaces. */
	public synchronized void setPersonalWords(Collection<String> entries) {
		personal.set(entries);
		update(s -> new Sources(s.languages(), s.gameWords(), personal.words()));
	}

	/** The personal dictionary's words, lowercase and sorted. */
	public synchronized List<String> personalWords() {
		return personal.all();
	}

	private synchronized void update(UnaryOperator<Sources> change) {
		sources = change.apply(sources);
	}

	public boolean isReady() {
		return sources.languages() != null;
	}

	public boolean isCorrect(String word) {
		return sources.isCorrect(word);
	}

	/** The misspelled words of {@code text}. Results are cached until any word source changes. */
	public synchronized List<WordSpan> misspelled(String text) {
		Sources current = sources;
		if (current.languages() == null) {
			return List.of();
		}
		if (current != cachedFor) {
			misspelledCache.clear();
			cachedFor = current;
		}
		return misspelledCache.computeIfAbsent(text, t -> {
			List<WordSpan> found = new ArrayList<>();
			for (WordSpan word : Tokenizer.words(t)) {
				if (!current.isCorrect(word.text())) {
					found.add(word);
				}
			}
			return List.copyOf(found);
		});
	}

	/** Up to {@code limit} corrections for a misspelled word, closest and most common first. */
	public List<String> suggest(String word, int limit) {
		List<WordList.Candidate> candidates = new ArrayList<>();
		for (WordList list : sources.lists()) {
			list.collectSuggestions(word, MAX_DISTANCE, candidates);
		}
		candidates.sort(BY_DISTANCE_THEN_RANK);
		return top(candidates, word, limit);
	}

	/** Up to {@code limit} words that start with {@code prefix}, most common first. */
	public List<String> complete(String prefix, int limit) {
		List<WordList.Candidate> candidates = new ArrayList<>();
		for (WordList list : sources.lists()) {
			list.collectCompletions(prefix, candidates);
		}
		candidates.sort(BY_RANK);
		return top(candidates, prefix, limit);
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
