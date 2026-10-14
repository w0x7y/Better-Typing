package com.bettertyping.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Maps a range of characters to screen rectangles, given how a field lays its text out in lines. */
public final class TextLayout {
	private TextLayout() {
	}

	/**
	 * Characters {@code [start, end)} of the text drawn on one row, the first at screen position
	 * {@code (x, y)} (top of the row) and every width multiplied by {@code scale}.
	 */
	public record Line(int start, int end, float x, float y, float scale) {
	}

	/** Part of one line on screen. {@code bottom} is the row right under the letters, where an underline goes. */
	public record Segment(int x1, int x2, int top, int bottom) {
	}

	/**
	 * The parts of {@code [start, end)} that are on screen, one per line. Lines a field doesn't show
	 * (scrolled away) simply aren't in {@code lines}.
	 *
	 * @param width text width in unscaled pixels
	 * @param lineHeight unscaled line height; letters fill all but its last row
	 */
	public static List<Segment> segments(String text, List<Line> lines, int start, int end, ToIntFunction<String> width, int lineHeight) {
		List<Segment> segments = new ArrayList<>();
		for (Line line : lines) {
			int from = Math.max(start, line.start());
			int to = Math.min(end, line.end());
			if (from >= to) {
				continue;
			}
			int x1 = Math.round(line.x() + width.applyAsInt(text.substring(line.start(), from)) * line.scale());
			int x2 = Math.round(line.x() + width.applyAsInt(text.substring(line.start(), to)) * line.scale());
			int top = Math.round(line.y());
			int bottom = Math.round(line.y() + (lineHeight - 1) * line.scale());
			segments.add(new Segment(x1, x2, top, bottom));
		}
		return segments;
	}
}
