package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
	}

	@Test
	void reportsPositions() {
		WordSpan word = Tokenizer.words("hi there").get(1);
		assertEquals(new WordSpan(3, 8, "there"), word);
	}

	@Test
	void findsWordAtCursor() {
		assertEquals("there", Tokenizer.wordAt("hi there you", 5).text());
		assertEquals("there", Tokenizer.wordAt("hi there you", 8).text());
		assertNull(Tokenizer.wordAt("hi  there", 3));
	}

	@Test
	void findsWordEndingAtCursorOnlyAtItsEnd() {
		assertEquals("ther", Tokenizer.wordEndingAt("hi ther", 7).text());
		assertNull(Tokenizer.wordEndingAt("hi there", 6));
		assertNull(Tokenizer.wordEndingAt("hi there ", 9));
	}
}
