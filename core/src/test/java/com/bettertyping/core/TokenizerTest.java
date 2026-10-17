package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizerTest {
	private static List<String> words(String text) {
		return Tokenizer.words(text).stream().map(WordSpan::text).toList();
	}

	@Test
	void splitsOnSpacesAndPunctuation() {
		assertEquals(List.of("Hello", "world", "well", "known"), words("Hello, world! well-known"));
	}

	@Test
	void keepsInnerApostrophesAndTrimsQuotes() {
		assertEquals(List.of("don't", "it’s", "quoted"), words("don't it’s 'quoted'"));
	}

	@Test
	void skipsNumbersSingleLettersAndLinks() {
		assertEquals(List.of("see"), words("a 42 abc123 x_y see https://example.com www.test.org me@mail.com play.hypixel.net"));
		assertEquals(List.of("see", "it", "version"), words("see readme.md it, version 1.2."), "a file name is skipped, a number isn't a link");
	}

	@Test
	void hebrewAcronymsAndVowelMarksStayInOneWord() {
		// A quote inside a Hebrew word is part of it; quotes around a word aren't, and a trailing geresh is trimmed like an apostrophe.
		assertEquals(List.of("צה\"ל", "צה״ל", "שלום", "וכו"), words("צה\"ל צה״ל \"שלום\" וכו׳"));
		assertEquals(List.of("مُحَمَّد", "رسول", "שָׁלוֹם"), words("مُحَمَّد رسول، שָׁלוֹם"));
	}

	@Test
	void normalizedWordsDropVowelMarksAndUnifyQuotes() {
		assertEquals("محمد", Tokenizer.normalize("مُحَمَّد"));
		assertEquals("שלום", Tokenizer.normalize("שָׁלוֹם"));
		assertEquals("צה\"ל", Tokenizer.normalize("צה״ל"));
		assertEquals("ג'וק", Tokenizer.normalize("ג׳וק"));
		assertEquals("don't", Tokenizer.normalize("don’t"));
	}

	@Test
	void reportsPositions() {
		WordSpan word = Tokenizer.words("hi there").get(1);
		assertEquals(new WordSpan(3, 8, "there"), word);
	}

	/** Text from a server, like a book page, is tokenized every frame, so a long chunk must stay cheap. */
	@Test
	void longChunkIsCheckedForLinksInLinearTime() {
		String chunk = "a." + "b".repeat(64_000) + "\u0085";
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> Tokenizer.words(chunk));
	}

	@Test
	void findsWordAtCursor() {
		assertEquals("there", Tokenizer.wordAt("hi there you", 5).text());
		assertEquals("there", Tokenizer.wordAt("hi there you", 8).text());
		assertNull(Tokenizer.wordAt("hi  there", 3));
	}

	@Test
	void wordBeingTypedEndsAtTheCursor() {
		assertEquals("ther", Tokenizer.wordBeingTyped(TextState.of("hi ther", 7)).text());
		assertNull(Tokenizer.wordBeingTyped(TextState.of("hi there", 6)));
		assertNull(Tokenizer.wordBeingTyped(TextState.of("hi there ", 9)));
		assertNull(Tokenizer.wordBeingTyped(new TextState("hi ther", 7, 3)), "nothing is being typed over a selection");
	}
}
