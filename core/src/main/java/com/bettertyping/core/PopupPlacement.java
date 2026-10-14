package com.bettertyping.core;

import java.util.List;

/**
 * Where the suggestion list goes: above its word like vanilla's command suggestions, unless that would
 * cover text. A sign has lines above and below, so the list tries below, then beside the text.
 */
public final class PopupPlacement {
	private static final int GAP = 3;
	// Rows under a line's letters that its squiggle uses.
	private static final int UNDERLINE_ROWS = 2;
	// The list's first row draws its text this far below its top.
	private static final int TEXT_INSET = 2;

	private PopupPlacement() {
	}

	/** The list's top left corner. */
	public record Position(int x, int y) {
	}

	/**
	 * @param text every piece of text on screen the list shouldn't cover, the word's own line included
	 */
	public static Position place(TextLayout.Segment word, int width, int height, List<TextLayout.Segment> text, int screenWidth, int screenHeight) {
		// Like vanilla, the text lines up with the word and the box starts one pixel to its left.
		int x = Math.max(0, Math.min(word.x1(), screenWidth - width) - 1);
		int above = word.top() - height - GAP;
		if (above >= 0 && !covers(x, above, width, height, text)) {
			return new Position(x, above);
		}
		int below = word.bottom() + GAP;
		if (below + height <= screenHeight && !covers(x, below, width, height, text)) {
			return new Position(x, below);
		}

		// Beside the text, the first row level with the word.
		int y = Math.max(0, Math.min(word.top() - TEXT_INSET, screenHeight - height));
		int left = word.x1();
		int right = word.x2();
		for (TextLayout.Segment segment : text) {
			if (overlapsRows(segment, y, height)) {
				left = Math.min(left, segment.x1());
				right = Math.max(right, segment.x2());
			}
		}
		if (right + GAP + width <= screenWidth) {
			return new Position(right + GAP, y);
		}
		if (left - GAP - width >= 0) {
			return new Position(left - GAP - width, y);
		}
		// Neither fits on a tiny screen: stay above rather than slide over the word.
		return new Position(x, above >= 0 || below + height > screenHeight ? Math.max(0, above) : below);
	}

	private static boolean covers(int x, int y, int width, int height, List<TextLayout.Segment> text) {
		for (TextLayout.Segment segment : text) {
			if (overlapsRows(segment, y, height) && segment.x1() < x + width && x < segment.x2()) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlapsRows(TextLayout.Segment segment, int y, int height) {
		return segment.top() < y + height && y < segment.bottom() + UNDERLINE_ROWS;
	}
}
