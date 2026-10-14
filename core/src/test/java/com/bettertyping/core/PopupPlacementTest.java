package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PopupPlacementTest {
	private static final int SCREEN_WIDTH = 400;
	private static final int SCREEN_HEIGHT = 240;
	private static final int WIDTH = 60;
	private static final int HEIGHT = 36;

	private static TextLayout.Segment line(int x1, int x2, int top) {
		return new TextLayout.Segment(x1, x2, top, top + 8);
	}

	private static PopupPlacement.Position place(TextLayout.Segment word, List<TextLayout.Segment> text) {
		return PopupPlacement.place(word, WIDTH, HEIGHT, text, SCREEN_WIDTH, SCREEN_HEIGHT);
	}

	@Test
	void chatGoesAboveTheWordLikeVanilla() {
		TextLayout.Segment word = line(40, 70, 200);
		assertEquals(new PopupPlacement.Position(39, 161), place(word, List.of(line(4, 120, 200))));
	}

	@Test
	void signLineGoesBelowWhenTheLineAboveHasTextUnderIt() {
		// Sign line 2 with "teh wrold" on line 1 right above the word, lines 3 and 4 empty.
		TextLayout.Segment word = line(190, 206, 70);
		List<TextLayout.Segment> sign = List.of(line(180, 230, 60), line(190, 206, 70));
		assertEquals(new PopupPlacement.Position(189, 81), place(word, sign));
	}

	@Test
	void textBesideTheListDoesNotMoveIt() {
		// Line 1 has text, but it ends before the list starts.
		TextLayout.Segment word = line(190, 206, 70);
		List<TextLayout.Segment> sign = List.of(line(150, 185, 60), line(190, 206, 70));
		assertEquals(new PopupPlacement.Position(189, 31), place(word, sign));
	}

	@Test
	void goesRightOfTheTextWhenAboveAndBelowAreTaken() {
		// No room above the word, and lines of text below it.
		TextLayout.Segment word = line(150, 168, 30);
		List<TextLayout.Segment> text = List.of(line(150, 260, 30), line(150, 262, 39), line(150, 255, 48), line(150, 259, 57),
			line(150, 240, 66));
		assertEquals(new PopupPlacement.Position(265, 28), place(word, text));
	}

	@Test
	void sidePlacementOnlyAvoidsLinesItWouldReach() {
		// A long line far below the list doesn't push it further right.
		TextLayout.Segment word = line(150, 168, 30);
		List<TextLayout.Segment> text = List.of(line(150, 260, 30), line(150, 262, 39), line(150, 395, 120));
		assertEquals(new PopupPlacement.Position(265, 28), place(word, text));
	}

	@Test
	void goesLeftWhenTheRightSideIsTooNarrow() {
		TextLayout.Segment word = line(150, 168, 30);
		List<TextLayout.Segment> text = List.of(line(150, 360, 30), line(150, 362, 39));
		assertEquals(new PopupPlacement.Position(87, 28), place(word, text));
	}

	@Test
	void sidePlacementStaysOnScreenVertically() {
		TextLayout.Segment word = line(150, 168, 226);
		List<TextLayout.Segment> text = List.of(line(150, 260, 217), line(150, 262, 226));
		assertEquals(new PopupPlacement.Position(265, SCREEN_HEIGHT - HEIGHT), place(word, text));
	}

	@Test
	void nothingFreeFallsBackToAboveTheWord() {
		// Text fills the screen's width on every line around the word.
		TextLayout.Segment word = line(150, 168, 100);
		List<TextLayout.Segment> text = List.of(line(0, 400, 91), line(0, 400, 100), line(0, 400, 109));
		assertEquals(new PopupPlacement.Position(149, 61), place(word, text));
	}

	@Test
	void shortScreenGoesBesideTheWord() {
		TextLayout.Segment word = line(40, 70, 20);
		assertEquals(new PopupPlacement.Position(73, 14), PopupPlacement.place(word, WIDTH, HEIGHT, List.of(word), SCREEN_WIDTH, 50));
	}

	@Test
	void tinyScreenStaysAboveRatherThanCoverTheWord() {
		TextLayout.Segment word = line(40, 70, 20);
		assertEquals(new PopupPlacement.Position(39, 0), PopupPlacement.place(word, WIDTH, HEIGHT, List.of(word), 100, 50));
	}

	@Test
	void listIsKeptOnScreenHorizontally() {
		TextLayout.Segment word = line(380, 396, 200);
		assertEquals(new PopupPlacement.Position(SCREEN_WIDTH - WIDTH - 1, 161), place(word, List.of(word)));
	}
}
