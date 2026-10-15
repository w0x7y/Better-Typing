package com.bettertyping.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Where a {@link TypingSession.Overlay} goes on screen: a squiggle under each misspelling, and the
 * suggestion list next to its word.
 *
 * <p>The list copies vanilla's command suggestions (CommandSuggestions.SuggestionsList) and goes above
 * its word, unless that would cover text it must keep clear of ({@link TypingField#keepClear()}). A sign
 * has lines above and below, so the list then tries below, then beside the text.
 */
public final class OverlayLayout {
	private static final int ROW_HEIGHT = 12;
	// A row's text starts this far right of and below the row's top left corner.
	private static final int TEXT_LEFT = 1;
	private static final int TEXT_TOP = 2;
	private static final int GAP = 3;
	// Rows under a line's letters that its squiggle uses.
	private static final int UNDERLINE_ROWS = 2;

	private OverlayLayout() {
	}

	/**
	 * What the layout needs from the game.
	 *
	 * @param lineHeight unscaled height of a line of text; letters fill all but its last row
	 * @param textWidth width of the suggestion list's text
	 * @param label the text shown for a row of the suggestion list
	 */
	public record Metrics(int screenWidth, int screenHeight, int lineHeight, ToIntFunction<String> textWidth,
		Function<Popup.Item, String> label) {
	}

	/** A wavy underline from {@code x1} to {@code x2} (exclusive) on rows {@code y} and {@code y + 1}. */
	public record Squiggle(int x1, int x2, int y) {
		/** The wave's row at column {@code x}: 0 for two pixels, then 1 for two pixels. */
		public int dy(int x) {
			return ((x - x1) >> 1) & 1;
		}
	}

	/** One row of the suggestion list; its text starts at {@code (x, y)}. */
	public record Row(String text, int x, int y, boolean highlighted) {
	}

	/** The suggestion list's background, from {@code (x1, y1)} to {@code (x2, y2)} (exclusive), and its rows. */
	public record SuggestionBox(int x1, int y1, int x2, int y2, List<Row> rows) {
	}

	/** @param suggestions null when there's no list, or its word is scrolled out of view */
	public record Drawing(List<Squiggle> squiggles, SuggestionBox suggestions) {
	}

	/** Part of one line on screen. {@code bottom} is the row right under the letters, where an underline goes. */
	private record Segment(int x1, int x2, int top, int bottom) {
	}

	private record Corner(int x, int y) {
	}

	public static Drawing layout(TypingSession.Overlay overlay, Metrics metrics) {
		List<Squiggle> squiggles = new ArrayList<>();
		for (TypingSession.Misspelling misspelling : overlay.misspellings()) {
			WordSpan word = misspelling.word();
			for (Segment segment : segments(misspelling.field(), word.start(), word.end(), metrics.lineHeight())) {
				squiggles.add(new Squiggle(segment.x1(), segment.x2(), segment.bottom()));
			}
		}
		return new Drawing(squiggles, suggestions(overlay, metrics));
	}

	private static SuggestionBox suggestions(TypingSession.Overlay overlay, Metrics metrics) {
		Popup popup = overlay.popup();
		if (popup == null) {
			return null;
		}
		List<Segment> word = segments(overlay.focused(), popup.word().start(), popup.word().end(), metrics.lineHeight());
		if (word.isEmpty()) {
			return null;
		}
		List<String> labels = new ArrayList<>();
		int textWidth = 0;
		for (Popup.Item item : popup.items()) {
			String label = metrics.label().apply(item);
			labels.add(label);
			textWidth = Math.max(textWidth, metrics.textWidth().applyAsInt(label));
		}
		int width = TEXT_LEFT + textWidth;
		int height = labels.size() * ROW_HEIGHT;
		Corner corner = place(word.get(0), width, height, keepClear(overlay, metrics.lineHeight()), metrics.screenWidth(), metrics.screenHeight());
		int x = corner.x();
		int y = corner.y();
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < labels.size(); i++) {
			rows.add(new Row(labels.get(i), x + TEXT_LEFT, y + i * ROW_HEIGHT + TEXT_TOP, i == popup.selected()));
		}
		return new SuggestionBox(x, y, x + width, y + height, rows);
	}

	/** The text on screen the suggestion list shouldn't cover, its word's own line included. */
	private static List<Segment> keepClear(TypingSession.Overlay overlay, int lineHeight) {
		List<Segment> text = new ArrayList<>();
		for (TypingField field : overlay.fields()) {
			if (field.keepClear()) {
				text.addAll(segments(field, 0, field.state().text().length(), lineHeight));
			}
		}
		return text;
	}

	/** The parts of characters {@code [start, end)} that are on screen, one per line. */
	private static List<Segment> segments(TypingField field, int start, int end, int lineHeight) {
		String text = field.state().text();
		List<Segment> segments = new ArrayList<>();
		for (TypingField.Line line : field.lines()) {
			int from = Math.max(start, line.start());
			int to = Math.min(end, line.end());
			if (from >= to) {
				continue;
			}
			int x1 = Math.round(line.x() + field.textWidth(text.substring(line.start(), from)) * line.scale());
			int x2 = Math.round(line.x() + field.textWidth(text.substring(line.start(), to)) * line.scale());
			int top = Math.round(line.y());
			int bottom = Math.round(line.y() + (lineHeight - 1) * line.scale());
			segments.add(new Segment(x1, x2, top, bottom));
		}
		return segments;
	}

	/** The list's top left corner: above the word, else below it, else beside the text. */
	private static Corner place(Segment word, int width, int height, List<Segment> text, int screenWidth, int screenHeight) {
		// Like vanilla, the text lines up with the word and the box starts just left of it.
		int x = Math.max(0, Math.min(word.x1(), screenWidth - width) - TEXT_LEFT);
		int above = word.top() - height - GAP;
		if (above >= 0 && !covers(x, above, width, height, text)) {
			return new Corner(x, above);
		}
		int below = word.bottom() + GAP;
		if (below + height <= screenHeight && !covers(x, below, width, height, text)) {
			return new Corner(x, below);
		}

		// Beside the text, the first row level with the word.
		int y = Math.max(0, Math.min(word.top() - TEXT_TOP, screenHeight - height));
		int left = word.x1();
		int right = word.x2();
		for (Segment segment : text) {
			if (overlapsRows(segment, y, height)) {
				left = Math.min(left, segment.x1());
				right = Math.max(right, segment.x2());
			}
		}
		if (right + GAP + width <= screenWidth) {
			return new Corner(right + GAP, y);
		}
		if (left - GAP - width >= 0) {
			return new Corner(left - GAP - width, y);
		}
		// Neither fits on a tiny screen: stay above rather than slide over the word.
		return new Corner(x, above >= 0 || below + height > screenHeight ? Math.max(0, above) : below);
	}

	private static boolean covers(int x, int y, int width, int height, List<Segment> text) {
		for (Segment segment : text) {
			if (overlapsRows(segment, y, height) && segment.x1() < x + width && x < segment.x2()) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlapsRows(Segment segment, int y, int height) {
		return segment.top() < y + height && y < segment.bottom() + UNDERLINE_ROWS;
	}
}
