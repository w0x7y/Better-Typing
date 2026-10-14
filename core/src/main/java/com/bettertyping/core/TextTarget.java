package com.bettertyping.core;

/** A text field the assistant can read and edit. Implemented for each Minecraft widget type. */
public interface TextTarget {
	TextState state();

	/** Replaces the whole content, then places the cursor and selection anchor. */
	void apply(TextState state);
}
