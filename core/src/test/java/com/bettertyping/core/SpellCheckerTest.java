package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs against the real bundled English dictionary. */
class SpellCheckerTest {
	static final Path DICTIONARY = Path.of("../common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt");
	static WordList english;

	@TempDir
	Path tmp;

	@BeforeAll
	static void loadDictionary() throws IOException {
		try (InputStream in = Files.newInputStream(DICTIONARY)) {
			english = WordList.fromRankedLines(in);
		}
	}

	private SpellChecker checker() throws IOException {
		PersonalDictionary personal = new PersonalDictionary(tmp.resolve("personal.txt"));
		personal.load();
		SpellChecker checker = new SpellChecker(personal);
		checker.setLanguage(english);
		return checker;
	}

	@Test
	void acceptsCommonWordsAndFlagsTypos() throws IOException {
		SpellChecker checker = checker();
		assertTrue(checker.isCorrect("receive"));
		assertTrue(checker.isCorrect("The"));
		assertFalse(checker.isCorrect("recieve"));
		assertFalse(checker.isCorrect("teh"));
	}

	@Test
	void suggestsTheMostLikelyCorrectionFirst() throws IOException {
		SpellChecker checker = checker();
		assertEquals("the", checker.suggest("teh", 3).get(0));
		assertEquals("receive", checker.suggest("recieve", 3).get(0));
		assertEquals("Because", checker.suggest("Becuase", 3).get(0));
		assertEquals("London", checker.suggest("london", 3).get(0));
	}

	@Test
	void completesWithCommonWordsFirst() throws IOException {
		List<String> completions = checker().complete("th", 3);
		assertEquals(List.of("the", "that", "this"), completions);
	}

	@Test
	void completionKeepsTheTypedCapitalization() throws IOException {
		assertTrue(checker().complete("Th", 3).stream().allMatch(w -> w.startsWith("Th")));
	}

	@Test
	void gameAndPersonalWordsCountAsCorrect() throws IOException {
		SpellChecker checker = checker();
		assertFalse(checker.isCorrect("netherite"));
		checker.setGameWords(WordList.caseInsensitive(List.of("Netherite"), 5_000));
		assertTrue(checker.isCorrect("Netherite"));

		assertFalse(checker.isCorrect("gg'ez"));
		checker.addPersonalWord("gg'ez");
		assertTrue(checker.isCorrect("gg'ez"));
	}

	@Test
	void misspellingsRefreshWhenAWordSourceChanges() throws IOException {
		SpellChecker checker = checker();
		assertEquals(List.of("netherite"), checker.misspelled("netherite ingot").stream().map(WordSpan::text).toList());
		checker.setGameWords(WordList.caseInsensitive(List.of("Netherite"), 5_000));
		assertEquals(List.of(), checker.misspelled("netherite ingot"));
	}

	@Test
	void wordAddedWhileSavingFailsStillCountsAndIsNotUnderlined() throws IOException {
		Path blocker = tmp.resolve("blocker");
		Files.writeString(blocker, "a file where a folder should be");
		PersonalDictionary personal = new PersonalDictionary(blocker.resolve("personal.txt"));
		personal.load();
		SpellChecker checker = new SpellChecker(personal);
		checker.setLanguage(english);
		assertEquals(1, checker.misspelled("zqxw").size());

		assertThrows(IOException.class, () -> checker.addPersonalWord("zqxw"));
		assertTrue(checker.isCorrect("zqxw"));
		assertEquals(List.of(), checker.misspelled("zqxw"));
	}

	@Test
	void personalDictionaryIsSaved() throws IOException {
		Path file = tmp.resolve("words.txt");
		PersonalDictionary personal = new PersonalDictionary(file);
		personal.load();
		personal.add("Skyblock");
		PersonalDictionary reloaded = new PersonalDictionary(file);
		reloaded.load();
		assertTrue(reloaded.contains("skyblock"));
		assertEquals(List.of("skyblock"), reloaded.all());
	}
}
