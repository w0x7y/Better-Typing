package com.bettertyping.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spell check, autocomplete and undo/redo for one text field.
 *
 * <p>Controls: Tab opens spelling suggestions for the misspelled word under the cursor, or moves the
 * highlight through the open list. Space accepts the highlighted suggestion; with nothing highlighted
 * it types a normal space. Escape closes the list.
 *
 * <p>{@code assist} is false for fields where spell check and autocomplete shouldn't run (commands);
 * undo/redo works everywhere.
 */
public final class TypingAssistant {
	public static final int SUGGESTIONS = 3;
	public static final int MIN_PREFIX = 2;

	private final SpellChecker checker;
	private final Settings settings;
	private final UndoHistory history = new UndoHistory();
	private TextState last;
	private Popup popup;
	private String checkedText;
	private int checkedVersion = -1;
	private List<WordSpan> checkedMisspelled = List.of();

	public TypingAssistant(SpellChecker checker, Settings settings) {
		this.checker = checker;
		this.settings = settings;
	}

	public Popup popup() {
		return popup;
	}

	/** Catches up with edits and cursor moves made since the last call. Call before anything else. */
	public void sync(TextTarget target, boolean assist, long nowMs) {
		TextState state = target.state();
		if (last == null) {
			history.reset(state);
			last = state;
			return;
		}
		if (state.equals(last)) {
			if (!assist) {
				popup = null;
			}
			return;
		}
		boolean textChanged = !state.text().equals(last.text());
		history.observe(state, nowMs);
		last = state;
		if (!assist) {
			popup = null;
		} else if (textChanged) {
			popup = autocompletePopup(state);
		} else if (popup != null && !popupStillValid(state)) {
			popup = null;
		}
	}

	public boolean onTab(TextTarget target, boolean assist, long nowMs) {
		sync(target, assist, nowMs);
		if (!assist) {
			return false;
		}
		if (popup != null) {
			popup.selectNext();
			return true;
		}
		if (!settings.spellCheck || !checker.isReady() || last.hasSelection()) {
			return false;
		}
		WordSpan word = Tokenizer.wordAt(last.text(), last.cursor());
		if (word == null || checker.isCorrect(word.text())) {
			return false;
		}
		List<Popup.Item> items = new ArrayList<>();
		for (String suggestion : checker.suggest(word.text(), SUGGESTIONS)) {
			items.add(new Popup.Item(suggestion, false));
		}
		items.add(new Popup.Item(word.text(), true));
		popup = new Popup(Popup.Mode.SPELLING, word, items, 0);
		return true;
	}

	/** Returns true when Space was used to accept a suggestion, so it must not type a space. */
	public boolean onSpace(TextTarget target, boolean assist, long nowMs) {
		sync(target, assist, nowMs);
		if (!assist || popup == null || popup.selected() < 0) {
			return false;
		}
		Popup accepted = popup;
		popup = null;
		Popup.Item item = accepted.items().get(accepted.selected());
		WordSpan word = accepted.word();
		if (item.addToDictionary()) {
			try {
				checker.addPersonalWord(word.text());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return true;
		}
		String text = last.text();
		if (word.end() > text.length() || !text.substring(word.start(), word.end()).equals(word.text())) {
			return false;
		}
		String before = text.substring(0, word.start());
		String after = text.substring(word.end());
		String replacement = item.text();
		int cursor = word.start() + replacement.length();
		if (accepted.mode() == Popup.Mode.AUTOCOMPLETE) {
			// Accepting a completion also ends the word, like on phone keyboards.
			if (after.isEmpty() || !Character.isWhitespace(after.charAt(0))) {
				replacement += " ";
			}
			cursor++;
		}
		target.apply(TextState.of(before + replacement + after, cursor));
		last = target.state();
		history.recordEdit(last);
		return true;
	}

	public boolean onEscape() {
		if (popup == null) {
			return false;
		}
		popup = null;
		return true;
	}

	public boolean undo(TextTarget target, long nowMs) {
		sync(target, false, nowMs);
		return restore(target, history.undo());
	}

	public boolean redo(TextTarget target, long nowMs) {
		sync(target, false, nowMs);
		return restore(target, history.redo());
	}

	/**
	 * Misspelled words to underline. The word being typed right now (ending at the cursor) is left
	 * alone until it's finished.
	 */
	public List<WordSpan> misspelled(String text, int cursor, boolean assist) {
		if (!assist || !settings.spellCheck || !checker.isReady()) {
			return List.of();
		}
		if (!text.equals(checkedText) || checker.version() != checkedVersion) {
			List<WordSpan> found = new ArrayList<>();
			for (WordSpan word : Tokenizer.words(text)) {
				if (!checker.isCorrect(word.text())) {
					found.add(word);
				}
			}
			checkedText = text;
			checkedVersion = checker.version();
			checkedMisspelled = found;
		}
		if (checkedMisspelled.isEmpty() || !endsWordAt(text, cursor)) {
			return checkedMisspelled;
		}
		List<WordSpan> result = new ArrayList<>(checkedMisspelled);
		result.removeIf(word -> word.end() == cursor);
		return result;
	}

	private boolean restore(TextTarget target, Optional<TextState> state) {
		popup = null;
		state.ifPresent(target::apply);
		last = target.state();
		return true;
	}

	private Popup autocompletePopup(TextState state) {
		if (!settings.autocomplete || state.hasSelection()) {
			return null;
		}
		WordSpan word = Tokenizer.wordEndingAt(state.text(), state.cursor());
		if (word == null || word.length() < MIN_PREFIX) {
			return null;
		}
		List<String> completions = checker.complete(word.text(), SUGGESTIONS);
		if (completions.isEmpty()) {
			return null;
		}
		List<Popup.Item> items = new ArrayList<>();
		for (String completion : completions) {
			items.add(new Popup.Item(completion, false));
		}
		return new Popup(Popup.Mode.AUTOCOMPLETE, word, items, -1);
	}

	private boolean popupStillValid(TextState state) {
		if (state.hasSelection()) {
			return false;
		}
		WordSpan word = popup.word();
		return popup.mode() == Popup.Mode.AUTOCOMPLETE ? state.cursor() == word.end() : word.contains(state.cursor());
	}

	private static boolean endsWordAt(String text, int cursor) {
		return cursor > 0 && cursor <= text.length() && Character.isLetter(text.charAt(cursor - 1))
			&& (cursor == text.length() || !Character.isLetter(text.charAt(cursor)));
	}
}
