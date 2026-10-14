package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UndoHistoryTest {
	private final UndoHistory history = new UndoHistory();
	private long time = 0;

	private void type(String text) {
		for (char c : text.toCharArray()) {
			TextState current = history.current();
			String next = current.text().substring(0, current.cursor()) + c + current.text().substring(current.cursor());
			history.observe(TextState.of(next, current.cursor() + 1), time += 50);
		}
	}

	private String undo() {
		return history.undo().orElseThrow().text();
	}

	@Test
	void groupsTypingByWord() {
		history.reset(TextState.of("", 0));
		type("hello world");
		assertEquals("hello ", undo());
		assertEquals("", undo());
		assertTrue(history.undo().isEmpty());
	}

	@Test
	void pauseStartsNewStep() {
		history.reset(TextState.of("", 0));
		type("ab");
		time += UndoHistory.GROUP_TIMEOUT_MS + 1;
		type("cd");
		assertEquals("ab", undo());
	}

	@Test
	void redoRestoresUndoneSteps() {
		history.reset(TextState.of("", 0));
		type("one two");
		undo();
		assertEquals("one two", history.redo().orElseThrow().text());
	}

	@Test
	void newEditClearsRedo() {
		history.reset(TextState.of("", 0));
		type("one two");
		undo();
		type("x");
		assertTrue(history.redo().isEmpty());
	}

	@Test
	void pasteIsItsOwnStep() {
		history.reset(TextState.of("", 0));
		type("hi");
		history.observe(TextState.of("hi pasted text", 14), time += 10);
		assertEquals("hi", undo());
	}

	@Test
	void groupsConsecutiveBackspaces() {
		history.reset(TextState.of("hello", 5));
		history.observe(TextState.of("hell", 4), time += 10);
		history.observe(TextState.of("hel", 3), time += 10);
		assertEquals("hello", undo());
	}

	@Test
	void keyCombosParseAndMatch() {
		KeyCombo redo = KeyCombo.parse("Ctrl+Shift+Z");
		assertEquals("ctrl+shift+z", redo.toString());
		assertTrue(redo.matches("z", true, true, false));
		assertTrue(!redo.matches("z", true, false, false));
	}
}
