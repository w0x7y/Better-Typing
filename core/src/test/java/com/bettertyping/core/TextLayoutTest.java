package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TextLayoutTest {
	/** Every character 6 pixels wide, like a monospace font. */
	private static final ToIntFunction<String> WIDTH = s -> s.length() * 6;
	private static final int LINE_HEIGHT = 9;

	@Test
	void underlineSitsOnTheLastRowOfItsOwnLine() {
		List<TextLayout.Segment> segments = TextLayout.segments("hello world",
			List.of(new TextLayout.Line(0, 11, 10, 20, 1)), 6, 11, WIDTH, LINE_HEIGHT);
		assertEquals(List.of(new TextLayout.Segment(46, 76, 20, 28)), segments);
	}

	@Test
	void wordsAreMeasuredFromTheStartOfTheirLine() {
		// "aaaa bbbb" wrapped after the space: the second line starts at character 5.
		List<TextLayout.Line> lines = List.of(new TextLayout.Line(0, 5, 0, 0, 1), new TextLayout.Line(5, 9, 0, 9, 1));
		assertEquals(List.of(new TextLayout.Segment(0, 24, 9, 17)), TextLayout.segments("aaaa bbbb", lines, 5, 9, WIDTH, LINE_HEIGHT));
	}

	@Test
	void hiddenTextHasNoSegments() {
		// A text box scrolled so that only characters 4 onward are shown.
		List<TextLayout.Line> shown = List.of(new TextLayout.Line(4, 10, 0, 0, 1));
		assertEquals(List.of(), TextLayout.segments("abc defghij", shown, 0, 3, WIDTH, LINE_HEIGHT));
		assertEquals(List.of(new TextLayout.Segment(0, 18, 0, 8)), TextLayout.segments("abc defghij", shown, 2, 7, WIDTH, LINE_HEIGHT));
	}

	@Test
	void scaleAppliesToWidthsAndHeight() {
		List<TextLayout.Line> sign = List.of(new TextLayout.Line(0, 4, 100, 50, 0.5F));
		assertEquals(List.of(new TextLayout.Segment(103, 112, 50, 54)), TextLayout.segments("abcd", sign, 1, 4, WIDTH, LINE_HEIGHT));
	}
}
