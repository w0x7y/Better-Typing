package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverlayLayoutTest {
	private static final int SCREEN_WIDTH = 400;
	private static final int SCREEN_HEIGHT = 240;
	/** Every character 6 pixels wide, like a monospace font. */
	private static final int CHAR = 6;
	private static final OverlayLayout.Metrics METRICS = metrics(SCREEN_WIDTH, SCREEN_HEIGHT);
	/** Three rows, the widest 60 pixels: the list is 61 x 36. */
	private static final List<Popup.Item> ITEMS = List.of(new Popup.Item("aaaaaaaaaa", false), new Popup.Item("bbbbb", false),
		new Popup.Item("ccc", true));

	/** A field showing its text on the given lines. */
	private record TestField(String text, List<TypingField.Line> lines, boolean keepClear) implements TypingField {
		@Override
		public Object key() {
			return this;
		}

		@Override
		public boolean assist() {
			return true;
		}

		@Override
		public TextState state() {
			return TextState.of(text, 0);
		}

		@Override
		public void apply(TextState state) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int textWidth(String text) {
			return text.length() * CHAR;
		}
	}

	private static OverlayLayout.Metrics metrics(int screenWidth, int screenHeight) {
		return new OverlayLayout.Metrics(screenWidth, screenHeight, 9, text -> text.length() * CHAR,
			item -> item.addToDictionary() ? "Add " + item.text() : item.text());
	}

	/** A one-line field with its text at {@code (x, y)}. */
	private static TestField line(String text, int x, int y) {
		return new TestField(text, List.of(new TypingField.Line(0, text.length(), x, y, 1)), true);
	}

	private static Popup popup(TypingField field, int start, int end, List<Popup.Item> items, int selected) {
		WordSpan word = new WordSpan(start, end, field.state().text().substring(start, end));
		return new Popup(Popup.Mode.SPELLING, word, items, selected);
	}

	/** The top left corner of the list for the word {@code [start, end)} of {@code focused}. */
	private static List<Integer> corner(List<TestField> fields, TestField focused, int start, int end, OverlayLayout.Metrics metrics) {
		OverlayLayout.SuggestionBox box = OverlayLayout.layout(
			new TypingSession.Overlay(List.copyOf(fields), focused, List.of(), popup(focused, start, end, ITEMS, -1)), metrics).suggestions();
		return List.of(box.x1(), box.y1());
	}

	private static List<Integer> corner(List<TestField> fields, TestField focused, int start, int end) {
		return corner(fields, focused, start, end, METRICS);
	}

	private static List<OverlayLayout.Squiggle> squiggles(TestField field, int start, int end) {
		WordSpan word = new WordSpan(start, end, field.text().substring(start, end));
		TypingSession.Overlay overlay = new TypingSession.Overlay(List.of(field), field, List.of(new TypingSession.Misspelling(field, word)), null);
		return OverlayLayout.layout(overlay, METRICS).squiggles();
	}

	@Test
	void underlineSitsOnTheLastRowOfItsOwnLine() {
		assertEquals(List.of(new OverlayLayout.Squiggle(46, 76, 28)), squiggles(line("hello world", 10, 20), 6, 11));
	}

	@Test
	void wordsAreMeasuredFromTheStartOfTheirLine() {
		// "aaaa bbbb" wrapped after the space: the second line starts at character 5.
		TestField page = new TestField("aaaa bbbb", List.of(new TypingField.Line(0, 5, 0, 0, 1), new TypingField.Line(5, 9, 0, 9, 1)), false);
		assertEquals(List.of(new OverlayLayout.Squiggle(0, 24, 17)), squiggles(page, 5, 9));
	}

	@Test
	void hiddenTextHasNoUnderline() {
		// A text box scrolled so that only characters 4 onward are shown.
		TestField box = new TestField("abc defghij", List.of(new TypingField.Line(4, 10, 0, 0, 1)), true);
		assertEquals(List.of(), squiggles(box, 0, 3));
		assertEquals(List.of(new OverlayLayout.Squiggle(0, 18, 8)), squiggles(box, 2, 7));
	}

	@Test
	void scaleAppliesToWidthsAndHeight() {
		TestField sign = new TestField("abcd", List.of(new TypingField.Line(0, 4, 100, 50, 0.5F)), true);
		assertEquals(List.of(new OverlayLayout.Squiggle(103, 112, 54)), squiggles(sign, 1, 4));
	}

	@Test
	void chatListGoesAboveTheWordLikeVanilla() {
		TestField chat = line("hello wrold there", 4, 200);
		OverlayLayout.SuggestionBox box = OverlayLayout.layout(
			new TypingSession.Overlay(List.of(chat), chat, List.of(), popup(chat, 6, 11, ITEMS, 1)), METRICS).suggestions();
		assertEquals(new OverlayLayout.SuggestionBox(39, 161, 100, 197, List.of(
			new OverlayLayout.Row("aaaaaaaaaa", 40, 163, false),
			new OverlayLayout.Row("bbbbb", 40, 175, true),
			new OverlayLayout.Row("Add ccc", 40, 187, false))), box);
	}

	@Test
	void widestLabelSetsTheWidth() {
		TestField chat = line("hello wrold", 4, 200);
		List<Popup.Item> items = List.of(new Popup.Item("world", false), new Popup.Item("wrold", true));
		OverlayLayout.SuggestionBox box = OverlayLayout.layout(
			new TypingSession.Overlay(List.of(chat), chat, List.of(), popup(chat, 6, 11, items, 0)), METRICS).suggestions();
		assertEquals(List.of(39, 173, 94, 197), List.of(box.x1(), box.y1(), box.x2(), box.y2()), "\"Add wrold\" is 54 pixels wide");
	}

	@Test
	void wordScrolledOutOfViewHasNoList() {
		TestField box = new TestField("abc defghij", List.of(new TypingField.Line(4, 10, 0, 0, 1)), true);
		TypingSession.Overlay overlay = new TypingSession.Overlay(List.of(box), box, List.of(), popup(box, 0, 3, ITEMS, -1));
		assertNull(OverlayLayout.layout(overlay, METRICS).suggestions());
	}

	@Test
	void signListGoesBelowWhenTheLineAboveHasTextUnderIt() {
		// Sign line 2 with "teh wrold" on line 1 right above the word, lines 3 and 4 empty.
		TestField word = line("abc", 190, 70);
		List<TestField> sign = List.of(line("teh wrold", 180, 60), word, line("", 200, 80), line("", 200, 90));
		assertEquals(List.of(189, 81), corner(sign, word, 0, 3));
	}

	@Test
	void textBesideTheListDoesNotMoveIt() {
		// Line 1 has text, but it ends before the list starts.
		TestField word = line("abc", 190, 70);
		assertEquals(List.of(189, 31), corner(List.of(line("abcde", 150, 60), word), word, 0, 3));
	}

	@Test
	void bookListStaysAboveItsWordOverThePage() {
		TestField page = new TestField("aaaa bbbb cccc", List.of(new TypingField.Line(0, 5, 100, 50, 1),
			new TypingField.Line(5, 10, 100, 59, 1), new TypingField.Line(10, 14, 100, 68, 1)), false);
		assertEquals(List.of(99, 20), corner(List.of(page), page, 5, 9));
	}

	@Test
	void goesRightOfTheTextWhenAboveAndBelowAreTaken() {
		// No room above the word, and lines of text below it.
		List<TestField> lines = List.of(line("x".repeat(18), 150, 30), line("x".repeat(19), 150, 39), line("x".repeat(17), 150, 48),
			line("x".repeat(18), 150, 57), line("x".repeat(15), 150, 66));
		assertEquals(List.of(267, 28), corner(lines, lines.get(0), 0, 3));
	}

	@Test
	void sidePlacementOnlyAvoidsLinesItWouldReach() {
		// A long line far below the list doesn't push it further right.
		List<TestField> lines = List.of(line("x".repeat(18), 150, 30), line("x".repeat(19), 150, 39), line("x".repeat(40), 150, 120));
		assertEquals(List.of(267, 28), corner(lines, lines.get(0), 0, 3));
	}

	@Test
	void goesLeftWhenTheRightSideIsTooNarrow() {
		List<TestField> lines = List.of(line("x".repeat(35), 150, 30), line("x".repeat(36), 150, 39));
		assertEquals(List.of(86, 28), corner(lines, lines.get(0), 0, 3));
	}

	@Test
	void sidePlacementStaysOnScreenVertically() {
		List<TestField> lines = List.of(line("x".repeat(18), 150, 217), line("x".repeat(19), 150, 226));
		assertEquals(List.of(267, SCREEN_HEIGHT - 36), corner(lines, lines.get(1), 0, 3));
	}

	@Test
	void nothingFreeFallsBackToAboveTheWord() {
		// Text fills the screen's width on every line around the word.
		List<TestField> lines = new ArrayList<>();
		for (int y : new int[] {91, 100, 109}) {
			lines.add(line("x".repeat(67), 0, y));
		}
		assertEquals(List.of(149, 61), corner(lines, lines.get(1), 25, 28));
	}

	@Test
	void shortScreenGoesBesideTheWord() {
		TestField word = line("abcde", 40, 20);
		assertEquals(List.of(73, 14), corner(List.of(word), word, 0, 5, metrics(SCREEN_WIDTH, 50)));
	}

	@Test
	void tinyScreenStaysAboveRatherThanCoverTheWord() {
		TestField word = line("abcde", 40, 20);
		// 61 pixels wide on a 100-pixel screen, so it's also pulled left a little to stay on screen.
		assertEquals(List.of(38, 0), corner(List.of(word), word, 0, 5, metrics(100, 50)));
	}

	@Test
	void listIsKeptOnScreenHorizontally() {
		TestField word = line("abc", 380, 200);
		assertEquals(List.of(SCREEN_WIDTH - 61 - 1, 161), corner(List.of(word), word, 0, 3));
	}
}
