package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WordListTest {
	private final WordList list = WordList.caseSensitive(Map.of("hello", 0, "London", 1, "in", 2, "IN", 3, "don't", 4));

	@Test
	void followsCapitalizationRules() {
		assertTrue(list.contains("hello"));
		assertTrue(list.contains("Hello"));
		assertTrue(list.contains("HELLO"));
		assertFalse(list.contains("hELLO"));
		assertTrue(list.contains("London"));
		assertTrue(list.contains("LONDON"));
		assertFalse(list.contains("london"));
	}

	@Test
	void treatsCurlyApostropheAsStraight() {
		assertTrue(list.contains("don’t"));
	}

	@Test
	void chatWordsMoveUpAndCountAsCorrect() throws IOException {
		WordList language = WordList.language(lines("the", "of", "and", "held", "help", "hello"), lines("# greetings", "hello", "", "gg"));
		List<WordList.Candidate> completions = new ArrayList<>();
		language.collectCompletions("hel", completions);
		completions.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
		assertEquals(List.of("hello", "held", "help"), completions.stream().map(WordList.Candidate::word).toList());
		assertTrue(language.contains("gg"));
		assertFalse(language.contains("# greetings"));
	}

	private static InputStream lines(String... lines) {
		return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void caseInsensitiveListsAcceptAnyCapitalization() {
		WordList game = WordList.caseInsensitive(List.of("Netherite", "Creeper"), 5);
		assertTrue(game.contains("netherite"));
		assertTrue(game.contains("CREEPER"));
	}

	@Test
	void editDistanceCountsSwapsAsOneEdit() {
		assertEquals(1, EditDistance.bounded("teh", "the", 2));
		assertEquals(1, EditDistance.bounded("recieve", "receive", 2));
		assertEquals(2, EditDistance.bounded("acommodate", "accommodated", 2));
		assertEquals(3, EditDistance.bounded("abc", "xyz", 2));
	}
}
