package com.bettertyping;

import net.minecraft.client.gui.Font;

/** The drawing calls the overlay needs. Each Minecraft version wraps its own GUI graphics class. */
public interface Canvas {
	void fill(int x1, int y1, int x2, int y2, int color);

	/** Draws text with a shadow, like vanilla GUI text. */
	void text(Font font, String text, int x, int y, int color);

	/** Draws everything after this call on top of what came before. */
	void nextStratum();
}
