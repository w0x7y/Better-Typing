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
	private static final KeyCombo TAB = KeyCombo.parse("tab");
	private static final String ESCAPE = "escape";

	private final SpellChecker checker;
	private final Settings settings;
	private final Map<Object, FieldAssistant> assistants = new HashMap<>();
	private Object focusedKey;

	public TypingSession(SpellChecker checker, Settings settings) {
		this.checker = checker;
		this.settings = settings;
	}

	/** What to draw: misspelled words in every field, and the suggestion list of the focused field (or null). */
	public record Overlay<F extends TypingField>(List<Misspelling<F>> misspellings, Popup popup) {
	}

	public record Misspelling<F extends TypingField>(F field, WordSpan word) {
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
		if (pressed.key().equals(ESCAPE)) {
			return assistant.escape();
		}
		return false;
	}

	/**
	 * Returns true when the character was used and must not be typed.
	 *
	 * @throws java.io.UncheckedIOException if a word was added to the personal dictionary but saving it failed
	 */
	public boolean onChar(TypingField focused, int codepoint, long nowMs) {
		FieldAssistant assistant = focus(focused, nowMs);
		return codepoint == ' ' && assistant.space(focused);
	}

	/** Catches up with the focused field's edits, then reports what to draw. */
	public <F extends TypingField> Overlay<F> overlay(List<F> fields, F focused, long nowMs) {
		FieldAssistant focusedAssistant = focus(focused, nowMs);
		List<Misspelling<F>> misspellings = new ArrayList<>();
		for (F field : fields) {
			for (WordSpan word : assistant(field).misspelled(field, field == focused)) {
				misspellings.add(new Misspelling<>(field, word));
			}
		}
		return new Overlay<>(misspellings, focusedAssistant.popup());
	}

	private FieldAssistant assistant(TypingField field) {
		return assistants.computeIfAbsent(field.key(), key -> new FieldAssistant(checker, settings));
	}

	private FieldAssistant focus(TypingField field, long nowMs) {
		if (!Objects.equals(focusedKey, field.key())) {
			FieldAssistant previous = assistants.get(focusedKey);
			if (previous != null) {
				previous.closePopup();
			}
			focusedKey = field.key();
		}
		FieldAssistant assistant = assistant(field);
		assistant.sync(field, nowMs);
		return assistant;
	}
}
