package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs against the real bundled English dictionary. */
class SpellCheckerTest {
	static final Path DICTIONARY = Path.of("../common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt");
	static final Path CHAT_WORDS = Path.of("../common/src/main/resources/assets/bettertyping/dictionaries/en_us.chat.txt");
	static WordList english;
	static WordList englishWithChat;

	@TempDir
	Path tmp;

	@BeforeAll
	static void loadDictionary() throws IOException {
		try (InputStream in = Files.newInputStream(DICTIONARY)) {
			english = WordList.fromRankedLines(in);
		}
		try (InputStream in = Files.newInputStream(DICTIONARY); InputStream chat = Files.newInputStream(CHAT_WORDS)) {
			englishWithChat = WordList.language(in, chat);
		}
	}

	private SpellChecker checker() {
		return checker(english);
	}

	private SpellChecker checker(WordList language) {
		SpellChecker checker = new SpellChecker(tmp.resolve("personal.txt"), e -> {
			throw new AssertionError(e);
		});
		checker.setLanguage(language);
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
	void chatWordsComeFirstWithoutPushingOutTheMostCommonWords() throws IOException {
		SpellChecker checker = checker(englishWithChat);
		assertEquals(List.of("hello", "help", "held"), checker.complete("hel", 3));
		assertEquals(List.of("the", "that", "this"), checker.complete("th", 3));
		assertEquals("please", checker.complete("pl", 3).get(0));
		assertTrue(checker.isCorrect("gg"));
		assertTrue(checker.isCorrect("AFK"));
		assertFalse(checker.isCorrect("teh"));
		assertEquals("the", checker.suggest("teh", 3).get(0));
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
		List<IOException> errors = new ArrayList<>();
		SpellChecker checker = new SpellChecker(blocker.resolve("personal.txt"), errors::add);
		checker.setLanguage(english);
		assertEquals(1, checker.misspelled("zqxw").size());

		checker.addPersonalWord("zqxw");
		assertEquals(1, errors.size());
		assertTrue(checker.isCorrect("zqxw"));
		assertEquals(List.of(), checker.misspelled("zqxw"));
	}

	@Test
	void personalDictionaryIsSaved() throws IOException {
		checker().addPersonalWord("Skyblock");
		assertEquals(List.of("skyblock"), Files.readAllLines(tmp.resolve("personal.txt")));
		SpellChecker reloaded = checker();
		assertTrue(reloaded.isCorrect("Skyblock"));
		assertEquals(List.of("skyblock"), reloaded.personalWords());
	}

	@Test
	void settingPersonalWordsReplacesThemAndSplitsEntries() throws IOException {
		SpellChecker checker = checker();
		checker.addPersonalWord("Skyblock");
		checker.addPersonalWord("gg'ez");
		checker.setPersonalWords(List.of("  Bedwars  Skywars ", "", "gg’ez"));
		assertEquals(List.of("bedwars", "gg'ez", "skywars"), checker.personalWords());
		assertEquals(List.of("bedwars", "gg'ez", "skywars"), Files.readAllLines(tmp.resolve("personal.txt")));
		assertFalse(checker.isCorrect("skyblock"));
		assertTrue(checker.isCorrect("Skywars"));
	}

	@Test
	void unreadablePersonalDictionaryIsLeftAlone() throws IOException {
		Path file = tmp.resolve("personal.txt");
		byte[] notUtf8 = {'c', 'a', 'f', (byte) 0xE9, '\n'};
		Files.write(file, notUtf8);
		List<IOException> errors = new ArrayList<>();
		SpellChecker checker = new SpellChecker(file, errors::add);
		checker.setLanguage(english);
		assertEquals(1, errors.size(), "reading fails");

		checker.addPersonalWord("zqxw");
		assertTrue(checker.isCorrect("zqxw"));
		assertEquals(2, errors.size(), "saving is refused");
		assertArrayEquals(notUtf8, Files.readAllBytes(file));
	}
}
