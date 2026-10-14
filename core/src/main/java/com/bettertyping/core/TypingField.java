package com.bettertyping.core;

/** A text field a {@link TypingSession} works on. Implemented for each kind of Minecraft text field. */
public interface TypingField {
	/** Identifies the field within its screen; each key keeps its own undo history. */
	Object key();

	/** Whether spell check and autocomplete apply. Undo/redo always does. */
	boolean assist();

	TextState state();

	/** Replaces the whole content, then places the cursor and selection anchor. The field may refuse text that doesn't fit. */
	void apply(TextState state);
}
