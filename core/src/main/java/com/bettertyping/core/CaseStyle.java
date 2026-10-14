package com.bettertyping.core;

import java.util.Locale;

/** How a typed word is capitalized, so suggestions can match it. */
public enum CaseStyle {
	LOWER,
	CAPITALIZED,
	UPPER,
	MIXED;

	public static CaseStyle of(String word) {
		boolean anyLetter = false;
		boolean allUpper = true;
		boolean restLower = true;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (!Character.isLetter(c)) {
				continue;
			}
			if (Character.isLowerCase(c)) {
				allUpper = false;
			} else if (anyLetter) {
				restLower = false;
			}
			anyLetter = true;
		}
		if (!anyLetter) {
			return LOWER;
		}
		if (allUpper && word.codePoints().filter(Character::isLetter).count() > 1) {
			return UPPER;
		}
		boolean firstUpper = Character.isUpperCase(firstLetter(word));
		if (firstUpper && restLower) {
			return CAPITALIZED;
		}
		return !firstUpper && restLower ? LOWER : MIXED;
	}

	/** Applies this style to a dictionary word. Proper nouns and mixed-case words keep their own capitals. */
	public String apply(String word) {
		return switch (this) {
			case UPPER -> word.toUpperCase(Locale.ROOT);
			case CAPITALIZED -> capitalize(word);
			case LOWER, MIXED -> word;
		};
	}

	static String capitalize(String word) {
		if (word.isEmpty()) {
			return word;
		}
		return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
	}

	private static char firstLetter(String word) {
		for (int i = 0; i < word.length(); i++) {
			if (Character.isLetter(word.charAt(i))) {
				return word.charAt(i);
			}
		}
		return ' ';
	}
}
