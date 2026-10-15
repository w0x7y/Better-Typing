package com.bettertyping.core;

import java.util.List;

/**
 * A field a {@link TypingSession} works on: its text, and where that text is on screen. Implemented for
 * each kind of Minecraft text field.
 */
public interface TypingField {
	/** Identifies the field within its screen; each key keeps its own undo history. */
	Object key();

	/** Whether spell check and autocomplete apply. Undo/redo always does. */
	boolean assist();

	TextState state();

	/** Replaces the whole content, then places the cursor and selection anchor. The field may refuse text that doesn't fit. */
	void apply(TextState state);

	/** Where the field draws its text right now, one entry per visible line. Lines scrolled out of view are left out. */
	List<Line> lines();

	/** Width of {@code text} in the field's own font, unscaled. */
	int textWidth(String text);

	/** Whether the suggestion list moves off this field's text. A book page's list stays by its word, over the page. */
	default boolean keepClear() {
		return true;
	}

	/**
	 * Characters {@code [start, end)} of the text drawn on one row, the first at screen position
	 * {@code (x, y)} (top of the row) and every width multiplied by {@code scale}.
	 */
	record Line(int start, int end, float x, float y, float scale) {
	}
}
