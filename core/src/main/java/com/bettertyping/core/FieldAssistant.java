package com.bettertyping.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spell check, autocomplete and undo/redo state for one field: its undo history (which also holds the
 * field's last known state) and its suggestion popup. Driven by {@link TypingSession}, which calls
 * {@link #sync} before anything else.
 */
final class FieldAssistant {
	private static final int SUGGESTIONS = 3;
	private static final int MIN_PREFIX = 2;

	private final SpellChecker checker;
	private final Settings settings;
	private final UndoHistory history = new UndoHistory();
	private Popup popup;

	FieldAssistant(SpellChecker checker, Settings settings) {
		this.checker = checker;
		this.settings = settings;
	}

	Popup popup() {
		return popup;
	}

	void closePopup() {
		popup = null;
	}

	/** Catches up with edits and cursor moves made since the last call. */
	void sync(TypingField field, long nowMs) {
		TextState state = field.state();
		UndoHistory.Change change = history.observe(state, nowMs);
		if (!field.assist()) {
			popup = null;
		} else if (change == UndoHistory.Change.TEXT) {
			popup = autocompletePopup(state);
		} else if (change == UndoHistory.Change.CURSOR && popup != null && !popupStillValid(state)) {
			popup = null;
		}
	}

	/** Moves the highlight through the open popup, or opens spelling suggestions for the word under the cursor. */
	boolean tab(TypingField field) {
		if (!field.assist()) {
			return false;
		}
		if (popup != null) {
			popup.selectNext();
			return true;
		}
		TextState state = history.current();
		if (!settings.spellCheck || !checker.isReady() || state.hasSelection()) {
			return false;
		}
		WordSpan word = Tokenizer.wordAt(state.text(), state.cursor());
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

	/**
	 * Accepts the highlighted suggestion. False when nothing is highlighted, so Space types normally. A
	 * field can refuse the new text (a full sign line): then a completion's Space types normally too, while
	 * in a spelling list, where the cursor is inside the word, it's dropped.
	 */
	boolean space(TypingField field) {
		if (!field.assist() || popup == null || popup.selected() < 0) {
			return false;
		}
		Popup accepted = popup;
		popup = null;
		Popup.Item item = accepted.items().get(accepted.selected());
		WordSpan word = accepted.word();
		if (item.addToDictionary()) {
			checker.addPersonalWord(word.text());
			return true;
		}
		String text = history.current().text();
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
		TextState requested = TextState.of(before + replacement + after, cursor);
		field.apply(requested);
		if (!field.state().text().equals(requested.text())) {
			field.apply(history.current());
			return accepted.mode() == Popup.Mode.SPELLING;
		}
		history.recordEdit(field.state());
		return true;
	}

	boolean escape() {
		if (popup == null) {
			return false;
		}
		popup = null;
		return true;
	}

	boolean undo(TypingField field) {
		return restore(field, history.undo());
	}

	boolean redo(TypingField field) {
		return restore(field, history.redo());
	}

	/** Undo/redo always consume the key, even with nothing left to undo. */
	private boolean restore(TypingField field, Optional<TextState> state) {
		popup = null;
		state.ifPresent(requested -> {
			field.apply(requested);
			TextState actual = field.state();
			if (!actual.equals(requested)) {
				history.settle(actual);
			}
		});
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
}
