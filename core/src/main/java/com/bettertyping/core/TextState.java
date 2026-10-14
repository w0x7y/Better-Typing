package com.bettertyping.core;

/** A text field's content, cursor position and selection anchor (equal to the cursor when nothing is selected). */
public record TextState(String text, int cursor, int anchor) {
	public static TextState of(String text, int cursor) {
		return new TextState(text, cursor, cursor);
	}

	public boolean hasSelection() {
		return cursor != anchor;
	}
}
