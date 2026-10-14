package com.bettertyping;

import com.bettertyping.core.SpellChecker;
import com.bettertyping.core.Tokenizer;
import com.bettertyping.core.WordList;
import com.bettertyping.core.WordSpan;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Item and mob names in the current game language, including modded ones, count as correct words. */
final class GameWords {
	private static final int RANK = 5_000;
	private static String builtFor;

	private GameWords() {
	}

	/** Rebuilds the list the first time it's needed and whenever the game language changes. */
	static void refreshIfNeeded(SpellChecker checker) {
		String language = Minecraft.getInstance().getLanguageManager().getSelected();
		if (language.equals(builtFor)) {
			return;
		}
		Set<String> words = new HashSet<>();
		for (Item item : BuiltInRegistries.ITEM) {
			addWords(I18n.get(item.getDescriptionId()), words);
		}
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			addWords(I18n.get(type.getDescriptionId()), words);
		}
		checker.setGameWords(WordList.caseInsensitive(words, RANK));
		builtFor = language;
	}

	private static void addWords(String name, Set<String> out) {
		for (WordSpan word : Tokenizer.words(name)) {
			out.add(word.text());
		}
	}
}
