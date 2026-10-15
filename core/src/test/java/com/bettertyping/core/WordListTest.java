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

	@Test
	void wordsAfterAnElisionCount() throws IOException {
		WordList french = WordList.language(lines("homme", "est", "aujourd'hui", "l'", "c'", "qu'"), InputStream.nullInputStream());
		assertTrue(french.contains("l'homme"));
		assertTrue(french.contains("L'Homme"));
		assertTrue(french.contains("c’est"));
		assertTrue(french.contains("qu'aujourd'hui"));
		assertFalse(french.contains("l'"));
		assertFalse(french.contains("x'homme"));
		assertFalse(french.contains("l'hommme"));
		assertEquals(3, french.size(), "elisions aren't words");
	}

	/** German-like: the 2 most common words can't be parts, "s" links parts, and "en" can be dropped. */
	private static WordList german() throws IOException {
		WordList.Compounds rules = WordList.Compounds.read(lines("compound.min_part=3", "compound.links=s es",
			"compound.stems=en e", "compound.skip_most_common=2"));
		return WordList.language(lines("der", "und", "Haus", "Tür", "spielen", "Platz", "Arbeit", "Zimmer", "hell", "blau", "Schule", "Hof"),
			InputStream.nullInputStream(), rules);
	}

	@Test
	void joinedWordsCountWhenTheLanguageMakesCompounds() throws IOException {
		WordList german = german();
		assertTrue(german.contains("Haustür"));
		assertTrue(german.contains("Spielplatz"), "spiel(en) + Platz");
		assertTrue(german.contains("Arbeitszimmer"), "Arbeit + s + Zimmer");
		assertTrue(german.contains("Schulhof"), "Schul(e) + Hof");
		assertTrue(german.contains("Hausarbeitszimmer"), "three parts");
		assertTrue(german.contains("hellblau"), "an adjective compound stays lowercase");
		assertTrue(german.contains("HAUSTÜR"));
	}

	@Test
	void compoundsFollowTheirLastPartAndSkipTheMostCommonWords() throws IOException {
		WordList german = german();
		assertFalse(german.contains("haustür"), "Tür is a noun, so the compound is capitalized");
		assertFalse(german.contains("Hausder"), "der is too common to be a part");
		assertFalse(german.contains("Undhaus"));
		assertFalse(german.contains("Hausxtür"), "x isn't a link");
		assertFalse(german.contains("Hau"), "one part isn't a compound");
		assertFalse(WordList.language(lines("Haus", "Tür"), InputStream.nullInputStream()).contains("Haustür"),
			"languages without compound rules don't join words");
	}

	@Test
	void lettersWithoutCaseFollowTheExactWord() throws IOException {
		WordList hebrew = WordList.language(lines("שלום", "תודה"), InputStream.nullInputStream());
		assertTrue(hebrew.contains("שלום"));
		assertTrue(hebrew.contains("שָׁלוֹם"), "vowel marks don't matter");
		assertFalse(hebrew.contains("שלוםם"));
		assertEquals(CaseStyle.LOWER, CaseStyle.of("שלום"));
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
