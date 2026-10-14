package com.bettertyping.field;

import com.bettertyping.core.TextTarget;
import java.util.List;

/** One editable piece of text on screen: a text box, a sign line or a book page. */
public interface Field {
	/** Identifies the field within its screen, so it keeps its own undo history. */
	Object key();

	TextTarget target();

	/** Whether spell check and autocomplete apply. Undo/redo always does. */
	boolean assist();

	/** Screen-space boxes covering characters {@code [start, end)}, one per visual line; empty if hidden. */
	List<Segment> segments(int start, int end);

	/**
	 * A horizontal run of text on screen. {@code bottom} is the row right under the letters (8 pixels below
	 * the top of a 9-pixel line), so an underline stays inside its own line in multi-line text.
	 */
	record Segment(int x1, int x2, int top, int bottom) {
	}
}
