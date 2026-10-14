package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypingAssistantTest {
	@TempDir
	Path tmp;

	private SpellChecker checker;
	private TypingAssistant assistant;
	private final Field field = new Field();
	private long time;

	/** A plain in-memory text field. */
	static final class Field implements TextTarget {
		TextState state = TextState.of("", 0);

		@Override
		public TextState state() {
			return state;
		}

		@Override
		public void apply(TextState state) {
			this.state = state;
		}

		void type(String text) {
			for (char c : text.toCharArray()) {
				String t = state.text();
				state = TextState.of(t.substring(0, state.cursor()) + c + t.substring(state.cursor()), state.cursor() + 1);
			}
		}
	}

	@BeforeEach
	void setUp() throws IOException {
		PersonalDictionary personal = new PersonalDictionary(tmp.resolve("personal.txt"));
		personal.load();
		checker = new SpellChecker(personal);
		checker.setLanguage(WordList.caseSensitive(Map.of(
			"the", 0, "there", 1, "then", 2, "them", 3, "hello", 4, "help", 5, "world", 6, "word", 7)));
		assistant = new TypingAssistant(checker, new Settings());
		assistant.sync(field, true, time);
	}

	private void typeAndSync(String text) {
		for (char c : text.toCharArray()) {
			field.type(String.valueOf(c));
			assistant.sync(field, true, time += 50);
		}
	}

	@Test
	void showsCompletionsWhileTypingWithNothingHighlighted() {
		typeAndSync("he");
		Popup popup = assistant.popup();
		assertNotNull(popup);
		assertEquals(Popup.Mode.AUTOCOMPLETE, popup.mode());
		assertEquals(List.of("hello", "help"), popup.items().stream().map(Popup.Item::text).toList());
		assertEquals(-1, popup.selected());
	}

	@Test
	void spaceWithoutHighlightTypesNormally() {
		typeAndSync("he");
		assertFalse(assistant.onSpace(field, true, time));
	}

	@Test
	void tabCyclesAndSpaceAcceptsCompletion() {
		typeAndSync("he");
		assertTrue(assistant.onTab(field, true, time));
		assertTrue(assistant.onTab(field, true, time));
		assertEquals(1, assistant.popup().selected());
		assertTrue(assistant.onSpace(field, true, time));
		assertEquals("help ", field.state.text());
		assertEquals(5, field.state.cursor());
		assertNull(assistant.popup());
	}

	@Test
	void tabWrapsAroundTheList() {
		typeAndSync("he");
		for (int i = 0; i < 3; i++) {
			assistant.onTab(field, true, time);
		}
		assertEquals(0, assistant.popup().selected());
	}

	@Test
	void tabOnMisspelledWordShowsCorrectionsAndAddToDictionary() {
		typeAndSync("wrold ");
		field.apply(TextState.of(field.state.text(), 2));
		assertTrue(assistant.onTab(field, true, time));
		Popup popup = assistant.popup();
		assertEquals(Popup.Mode.SPELLING, popup.mode());
		assertEquals(0, popup.selected());
		assertEquals("world", popup.items().get(0).text());
		assertTrue(popup.items().get(popup.items().size() - 1).addToDictionary());

		assertTrue(assistant.onSpace(field, true, time));
		assertEquals("world ", field.state.text());
	}

	@Test
	void addToDictionaryStopsFlaggingTheWord() {
		typeAndSync("gg ");
		field.apply(TextState.of("gg ", 1));
		assistant.onTab(field, true, time);
		int last = assistant.popup().items().size() - 1;
		for (int i = 0; i < last; i++) {
			assistant.onTab(field, true, time);
		}
		assertTrue(assistant.onSpace(field, true, time));
		assertTrue(checker.isCorrect("gg"));
		assertEquals("gg ", field.state.text());
	}

	@Test
	void tabOnCorrectWordIsLeftToTheGame() {
		typeAndSync("hello ");
		assertFalse(assistant.onTab(field, true, time));
	}

	@Test
	void escapeClosesThePopup() {
		typeAndSync("he");
		assertTrue(assistant.onEscape());
		assertNull(assistant.popup());
		assertFalse(assistant.onEscape());
	}

	@Test
	void noAssistInCommands() {
		field.type("/he");
		assistant.sync(field, false, time += 50);
		assertNull(assistant.popup());
		assertFalse(assistant.onTab(field, false, time));
		assertTrue(assistant.misspelled("/he wrold", 0, false).isEmpty());
	}

	@Test
	void underlinesFinishedMisspelledWordsOnly() {
		assertEquals(List.of("wrold"), names(assistant.misspelled("wrold hello", 11, true)));
		assertEquals(List.of(), names(assistant.misspelled("hello wrold", 11, true)));
	}

	@Test
	void acceptedSuggestionCanBeUndone() {
		typeAndSync("he");
		assistant.onTab(field, true, time);
		assistant.onSpace(field, true, time);
		assertEquals("hello ", field.state.text());
		assistant.undo(field, time += 10);
		assertEquals("he", field.state.text());
		assistant.redo(field, time += 10);
		assertEquals("hello ", field.state.text());
	}

	private static List<String> names(List<WordSpan> words) {
		return words.stream().map(WordSpan::text).toList();
	}
}
