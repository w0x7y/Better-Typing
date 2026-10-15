package com.bettertyping.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spell check, autocomplete and undo/redo for the text fields of one screen.
 *
 * <p>Keys, checked in this order: the undo and redo shortcuts from {@link Settings}; Tab (no modifiers)
 * opens spelling suggestions for the misspelled word under the cursor or moves the highlight through the
 * open list; Escape closes the list. A typed space accepts the highlighted suggestion; with nothing
 * highlighted it types normally.
 *
 * <p>Each field (by {@link TypingField#key()}) keeps its own undo history. Typing in another field
 * closes the suggestion list left open in the previous one.
 */
public final class TypingSession {
	private static final KeyCombo TAB = new KeyCombo(KeyCombo.TAB, false, false, false);

	private final SpellChecker checker;
	private final Settings settings;
	private final Map<Object, FieldAssistant> assistants = new HashMap<>();
	private Object focusedKey;

	public TypingSession(SpellChecker checker, Settings settings) {
		this.checker = checker;
		this.settings = settings;
	}

	/**
	 * What to draw on the screen: misspelled words in every field, and the focused field's suggestion list
	 * (or null). {@link OverlayLayout} works out where.
	 */
	public record Overlay(List<TypingField> fields, TypingField focused, List<Misspelling> misspellings, Popup popup) {
	}

	public record Misspelling(TypingField field, WordSpan word) {
	}

	/** Returns true when the key was used and must not reach the game. */
	public boolean onKey(TypingField focused, KeyCombo pressed, long nowMs) {
		FieldAssistant assistant = focus(focused, nowMs);
		if (settings.undoRedo) {
			if (pressed.equals(settings.undoKey)) {
				return assistant.undo(focused);
			}
			if (pressed.equals(settings.redoKey) || pressed.equals(settings.redoAltKey)) {
				return assistant.redo(focused);
			}
		}
		if (pressed.equals(TAB)) {
			return assistant.tab(focused);
		}
		if (pressed.key().equals(KeyCombo.ESCAPE)) {
			return assistant.escape();
		}
		return false;
	}

	/** Returns true when the character was used and must not be typed. */
	public boolean onChar(TypingField focused, int codepoint, long nowMs) {
		FieldAssistant assistant = focus(focused, nowMs);
		return codepoint == ' ' && assistant.space(focused);
	}

	/** Catches up with the focused field's edits, then reports what to draw. */
	public Overlay overlay(List<? extends TypingField> fields, TypingField focused, long nowMs) {
		Popup popup = focus(focused, nowMs).popup();
		List<Misspelling> misspellings = new ArrayList<>();
		for (TypingField field : fields) {
			for (WordSpan word : misspelled(field, field == focused)) {
				misspellings.add(new Misspelling(field, word));
			}
		}
		return new Overlay(List.copyOf(fields), focused, misspellings, popup);
	}

	/**
	 * Misspelled words to underline. In the focused field, the word being typed (ending at the cursor) is
	 * left alone until it's finished.
	 */
	private List<WordSpan> misspelled(TypingField field, boolean focused) {
		if (!field.assist() || !settings.spellCheck) {
			return List.of();
		}
		TextState state = field.state();
		List<WordSpan> words = checker.misspelled(state.text());
		WordSpan typing = focused && !state.hasSelection() ? Tokenizer.wordEndingAt(state.text(), state.cursor()) : null;
		if (typing == null || words.isEmpty()) {
			return words;
		}
		List<WordSpan> result = new ArrayList<>(words);
		result.removeIf(word -> word.start() == typing.start() && word.end() == typing.end());
		return result;
	}

	private FieldAssistant focus(TypingField field, long nowMs) {
		if (!Objects.equals(focusedKey, field.key())) {
			FieldAssistant previous = assistants.get(focusedKey);
			if (previous != null) {
				previous.closePopup();
			}
			focusedKey = field.key();
		}
		FieldAssistant assistant = assistants.computeIfAbsent(field.key(), key -> new FieldAssistant(checker, settings));
		assistant.sync(field, nowMs);
		return assistant;
	}
}
