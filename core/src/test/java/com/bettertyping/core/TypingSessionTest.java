package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypingSessionTest {
	private static final KeyCombo TAB = KeyCombo.parse("tab");
	private static final KeyCombo ESCAPE = KeyCombo.parse("escape");
	private static final KeyCombo UNDO = KeyCombo.parse("ctrl+z");
	private static final KeyCombo REDO = KeyCombo.parse("ctrl+shift+z");
	private static final KeyCombo REDO_ALT = KeyCombo.parse("ctrl+y");

	@TempDir
	Path tmp;

	private SpellChecker checker;
	private Settings settings;
	private TypingSession session;
	private final Field chat = new Field("chat", true);
	private long time;

	/** An in-memory text field. */
	static class Field implements TypingField {
		private final Object key;
		boolean assist;
		TextState state = TextState.of("", 0);

		Field(Object key, boolean assist) {
			this.key = key;
			this.assist = assist;
		}

		@Override
		public Object key() {
			return key;
		}

		@Override
		public boolean assist() {
			return assist;
		}

		@Override
		public TextState state() {
			return state;
		}

		@Override
		public void apply(TextState state) {
			this.state = state;
		}

		@Override
		public List<Line> lines() {
			return List.of(new Line(0, text().length(), 0, 0, 1));
		}

		@Override
		public int textWidth(String text) {
			return text.length() * 6;
		}

		String text() {
			return state.text();
		}

		void moveCursor(int cursor) {
			state = TextState.of(state.text(), cursor);
		}
	}

	@BeforeEach
	void setUp() {
		checker = new SpellChecker(tmp.resolve("personal.txt"), e -> {
			throw new AssertionError(e);
		});
		checker.setLanguages(List.of(WordList.caseSensitive(Map.of(
			"the", 0, "there", 1, "then", 2, "them", 3, "hello", 4, "help", 5, "world", 6, "word", 7))));
		settings = new Settings();
		session = new TypingSession(checker, settings);
	}

	/** Types like a player: one character at a time, the game redrawing in between. */
	private void type(Field field, String text) {
		for (char c : text.toCharArray()) {
			if (!session.onChar(field, c, time += 50)) {
				String t = field.text();
				int cursor = field.state.cursor();
				field.state = TextState.of(t.substring(0, cursor) + c + t.substring(cursor), cursor + 1);
			}
			session.overlay(List.of(field), field, time);
		}
	}

	private boolean press(Field field, KeyCombo key) {
		return session.onKey(field, key, time += 10);
	}

	private Popup popup(Field field) {
		return session.overlay(List.of(field), field, time).popup();
	}

	private List<String> misspelled(Field field) {
		return session.overlay(List.of(field), field, time).misspellings().stream().map(m -> m.word().text()).toList();
	}

	@Test
	void showsCompletionsWhileTypingWithNothingHighlighted() {
		type(chat, "he");
		Popup popup = popup(chat);
		assertNotNull(popup);
		assertEquals(Popup.Mode.AUTOCOMPLETE, popup.mode());
		assertEquals(List.of("hello", "help"), popup.items().stream().map(Popup.Item::text).toList());
		assertEquals(-1, popup.selected());
	}

	@Test
	void spaceWithoutHighlightTypesNormally() {
		type(chat, "he ");
		assertEquals("he ", chat.text());
	}

	@Test
	void tabCyclesAndSpaceAcceptsCompletion() {
		type(chat, "he");
		assertTrue(press(chat, TAB));
		assertTrue(press(chat, TAB));
		assertEquals(1, popup(chat).selected());
		type(chat, " ");
		assertEquals("help ", chat.text());
		assertEquals(5, chat.state.cursor());
		assertNull(popup(chat));
	}

	@Test
	void tabWrapsAroundTheList() {
		type(chat, "he");
		for (int i = 0; i < 3; i++) {
			press(chat, TAB);
		}
		assertEquals(0, popup(chat).selected());
	}

	@Test
	void tabOnMisspelledWordShowsCorrectionsAndAddToDictionary() {
		type(chat, "wrold ");
		chat.moveCursor(2);
		assertTrue(press(chat, TAB));
		Popup popup = popup(chat);
		assertEquals(Popup.Mode.SPELLING, popup.mode());
		assertEquals(0, popup.selected());
		assertEquals("world", popup.items().get(0).text());
		assertTrue(popup.items().get(popup.items().size() - 1).addToDictionary());

		type(chat, " ");
		assertEquals("world ", chat.text());
	}

	@Test
	void addToDictionaryStopsFlaggingTheWord() {
		type(chat, "gg ");
		chat.moveCursor(1);
		press(chat, TAB);
		assertEquals(List.of("gg"), popup(chat).items().stream().map(Popup.Item::text).toList());
		type(chat, " ");
		assertTrue(checker.isCorrect("gg"));
		assertEquals("gg ", chat.text());
		assertEquals(List.of(), misspelled(chat));
	}

	@Test
	void tabOnCorrectWordIsLeftToTheGame() {
		type(chat, "hello ");
		assertFalse(press(chat, TAB));
	}

	@Test
	void tabWithModifiersIsLeftToTheGame() {
		type(chat, "wrold ");
		chat.moveCursor(2);
		assertFalse(press(chat, KeyCombo.parse("ctrl+tab")));
		assertFalse(press(chat, KeyCombo.parse("shift+tab")));
		assertNull(popup(chat));
	}

	@Test
	void escapeClosesThePopupFirst() {
		type(chat, "he");
		assertTrue(press(chat, ESCAPE));
		assertNull(popup(chat));
		assertFalse(press(chat, ESCAPE));
	}

	@Test
	void noSpellCheckOrAutocompleteWithoutAssist() {
		Field command = new Field("command", false);
		type(command, "/he wrold ");
		assertNull(popup(command));
		command.moveCursor(5);
		assertFalse(press(command, TAB));
		assertEquals(List.of(), misspelled(command));
	}

	@Test
	void underlinesFinishedMisspelledWordsOnly() {
		type(chat, "wrold hello");
		assertEquals(List.of("wrold"), misspelled(chat));
		chat.apply(TextState.of("hello wrold", 11));
		assertEquals(List.of(), misspelled(chat));
		chat.moveCursor(0);
		assertEquals(List.of("wrold"), misspelled(chat));
	}

	@Test
	void underlinesEveryFieldButOnlyTheFocusedOneHasAPopup() {
		Field line1 = new Field("line1", true);
		Field line2 = new Field("line2", true);
		line1.apply(TextState.of("wrold", 5));
		type(line2, "he");
		TypingSession.Overlay overlay = session.overlay(List.of(line1, line2), line2, time);
		assertEquals(List.<TypingField>of(line1), overlay.misspellings().stream().map(TypingSession.Misspelling::field).toList());
		assertEquals(Popup.Mode.AUTOCOMPLETE, overlay.popup().mode());
	}

	@Test
	void acceptedSuggestionIsOneUndoStep() {
		type(chat, "he");
		press(chat, TAB);
		type(chat, " ");
		assertEquals("hello ", chat.text());
		assertTrue(press(chat, UNDO));
		assertEquals("he", chat.text());
		assertTrue(press(chat, REDO));
		assertEquals("hello ", chat.text());
	}

	@Test
	void ctrlYRedoesToo() {
		type(chat, "one two");
		press(chat, UNDO);
		assertEquals("one ", chat.text());
		assertTrue(press(chat, REDO_ALT));
		assertEquals("one two", chat.text());
	}

	@Test
	void undoKeysPassThroughWhenUndoIsOff() {
		settings.undoRedo = false;
		type(chat, "one two");
		assertFalse(press(chat, UNDO));
		assertEquals("one two", chat.text());
	}

	@Test
	void reboundUndoKeyIsUsed() {
		settings.undoKey = KeyCombo.parse("alt+u");
		type(chat, "one two");
		assertFalse(press(chat, UNDO));
		assertTrue(press(chat, KeyCombo.parse("alt+u")));
		assertEquals("one ", chat.text());
	}

	@Test
	void eachFieldKeepsItsOwnUndoHistory() {
		Field page1 = new Field("page1", true);
		Field page2 = new Field("page2", true);
		type(page1, "first");
		type(page2, "second");
		press(page1, UNDO);
		assertEquals("", page1.text());
		assertEquals("second", page2.text());
	}

	@Test
	void switchingFieldsClosesThePopupLeftOpen() {
		Field line1 = new Field("line1", true);
		Field line2 = new Field("line2", true);
		type(line1, "he");
		assertNotNull(popup(line1));
		session.overlay(List.of(line1, line2), line2, time);
		assertNull(session.overlay(List.of(line1, line2), line1, time).popup());
	}

	/** Like a sign line: refuses text longer than 5 characters and keeps what it had. */
	private static Field narrowField() {
		return new Field("sign", true) {
			@Override
			public void apply(TextState state) {
				if (state.text().length() <= 5) {
					super.apply(state);
				}
			}
		};
	}

	@Test
	void completionThatDoesNotFitLetsSpaceTypeNormally() {
		Field narrow = narrowField();
		type(narrow, "he");
		press(narrow, TAB);
		type(narrow, " ");
		assertEquals("he ", narrow.text(), "\"hello \" is too long, so Space types a space");
		assertEquals(3, narrow.state.cursor());
		assertNull(popup(narrow));
	}

	@Test
	void undoSettlesOnWhatTheFieldAccepted() {
		Field narrow = narrowField();
		type(narrow, "abcde");
		narrow.state = TextState.of("abcdefgh", 8);
		session.overlay(List.of(narrow), narrow, time += 2_000);
		press(narrow, UNDO);
		assertEquals("abcde", narrow.text());
		press(narrow, REDO);
		assertEquals("abcde", narrow.text(), "the field refused the longer text");
		type(narrow, "");
		press(narrow, UNDO);
		assertEquals("", narrow.text(), "history continues from what the field actually holds");
	}
}
