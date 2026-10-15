package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageDictionaryTest {
	private final List<String> skipped = new ArrayList<>();

	/** Files held in memory; reading one whose content is "unreadable" fails. */
	private static LanguageDictionary.Source files(Map<String, String> contents) {
		return name -> {
			if (!contents.containsKey(name)) {
				return null;
			}
			String content = contents.get(name);
			if (content.equals("unreadable")) {
				throw new IOException("Broken " + name);
			}
			return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		};
	}

	private Optional<LanguageDictionary> load(List<String> languages, Map<String, String> contents) {
		return LanguageDictionary.loadFirst(languages, files(contents), (language, e) -> skipped.add(language));
	}

	@Test
	void usesTheFirstLanguageThatHasAWordList() {
		LanguageDictionary dictionary = load(List.of("xx_xx", "en_us", "de_de"),
			Map.of("en_us.txt", "the\nhello\n", "en_us.chat.txt", "gg\n", "de_de.txt", "der\n")).orElseThrow();
		assertEquals("en_us", dictionary.language());
		assertTrue(dictionary.words().contains("hello"));
		assertTrue(dictionary.words().contains("gg"));
		assertFalse(dictionary.words().contains("der"));
		assertEquals(List.of("xx_xx"), skipped);
	}

	@Test
	void everyLanguageGetsItsOwnChatWordsAndTheSharedOnes() {
		Map<String, String> files = Map.of("de_de.txt", "der\nhallo\n", "de_de.chat.txt", "digga", "en_us.txt", "the\n",
			"en_us.chat.txt", "hello\n", "chat.txt", "gg\nafk\n");
		LanguageDictionary german = load(List.of("de_de"), files).orElseThrow();
		assertTrue(german.words().contains("digga"));
		assertTrue(german.words().contains("gg"));
		assertFalse(german.words().contains("hello"));
		assertTrue(load(List.of("en_us"), files).orElseThrow().words().contains("afk"));
	}

	@Test
	void rulesFileTurnsOnCompounds() {
		Map<String, String> files = Map.of("de_de.txt", "Haus\nTür\n", "de_de.rules.txt", "compound.min_part=3\n", "nl_nl.txt", "huis\ndeur\n");
		assertTrue(load(List.of("de_de"), files).orElseThrow().words().contains("Haustür"));
		assertFalse(load(List.of("nl_nl"), files).orElseThrow().words().contains("huisdeur"));
	}

	@Test
	void chatWordsAreOptional() {
		LanguageDictionary dictionary = load(List.of("en_us"), Map.of("en_us.txt", "the\nhello\n")).orElseThrow();
		assertTrue(dictionary.words().contains("hello"));
		assertEquals(2, dictionary.words().size());
	}

	@Test
	void unreadableLanguageIsSkipped() {
		LanguageDictionary dictionary = load(List.of("en_gb", "en_us"),
			Map.of("en_gb.txt", "unreadable", "en_us.txt", "hello\n")).orElseThrow();
		assertEquals("en_us", dictionary.language());
		assertEquals(List.of("en_gb"), skipped);
	}

	@Test
	void noLanguageFound() {
		assertTrue(load(List.of("xx_xx", "yy_yy"), Map.of()).isEmpty());
		assertEquals(List.of("xx_xx", "yy_yy"), skipped);
	}
}
