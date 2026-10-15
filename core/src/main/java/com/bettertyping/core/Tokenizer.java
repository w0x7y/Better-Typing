package com.bettertyping.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Splits text into checkable words, skipping URLs, emails, numbers and single letters. */
public final class Tokenizer {
	private static final Pattern DOMAIN = Pattern.compile(".*[\\p{L}\\d]\\.\\p{L}{2,}.*");

	private Tokenizer() {
	}

	/** Every checkable word in {@code text}. */
	public static List<WordSpan> words(String text) {
		List<WordSpan> words = new ArrayList<>();
		int i = 0;
		int length = text.length();
		while (i < length) {
			if (Character.isWhitespace(text.charAt(i))) {
				i++;
				continue;
			}
			int chunkEnd = i;
			while (chunkEnd < length && !Character.isWhitespace(text.charAt(chunkEnd))) {
				chunkEnd++;
			}
			if (!isLinkLike(text.substring(i, chunkEnd))) {
				collectRuns(text, i, chunkEnd, words);
			}
			i = chunkEnd;
		}
		return words;
	}

	/** The word containing {@code pos} (or ending exactly at it), or null. */
	public static WordSpan wordAt(String text, int pos) {
		for (WordSpan word : words(text)) {
			if (word.contains(pos)) {
				return word;
			}
		}
		return null;
	}

	/** The word that ends exactly at {@code pos}, with no word character right after it, or null. */
	public static WordSpan wordEndingAt(String text, int pos) {
		if (pos < text.length() && isWordChar(text, pos)) {
			return null;
		}
		for (WordSpan word : words(text)) {
			if (word.end() == pos) {
				return word;
			}
		}
		return null;
	}

	/**
	 * A word as dictionaries spell it: curly apostrophes and the Hebrew geresh count as straight
	 * apostrophes, the gershayim as a double quote (צה״ל), and Arabic and Hebrew vowel marks and the
	 * Arabic tatweel are left out.
	 */
	public static String normalize(String word) {
		StringBuilder normal = null;
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			char replacement = switch (c) {
				case '’', '\u05F3' -> '\'';
				case '\u05F4' -> '"';
				default -> c;
			};
			boolean dropped = isOptionalMark(c);
			if (normal == null && (dropped || replacement != c)) {
				normal = new StringBuilder(word.length()).append(word, 0, i);
			}
			if (normal != null && !dropped) {
				normal.append(replacement);
			}
		}
		return normal == null ? word : normal.toString();
	}

	/** Arabic and Hebrew vowel and cantillation marks, and the Arabic tatweel: written or not, it's the same word. */
	private static boolean isOptionalMark(char c) {
		return (c >= '\u0591' && c <= '\u05C7' && Character.getType(c) == Character.NON_SPACING_MARK)
			|| (c >= '\u0610' && c <= '\u061A') || (c >= '\u064B' && c <= '\u065F') || c == '\u0670' || c == '\u0640'
			|| (c >= '\u06D6' && c <= '\u06ED' && Character.getType(c) == Character.NON_SPACING_MARK);
	}

	static boolean isLinkLike(String chunk) {
		return chunk.contains("://") || chunk.startsWith("www.") || chunk.indexOf('@') >= 0
			|| chunk.startsWith("/") || DOMAIN.matcher(chunk).matches();
	}

	private static void collectRuns(String text, int from, int to, List<WordSpan> out) {
		int i = from;
		while (i < to) {
			if (!isRunChar(text, i)) {
				i++;
				continue;
			}
			int runEnd = i;
			boolean checkable = true;
			while (runEnd < to && isRunChar(text, runEnd)) {
				char c = text.charAt(runEnd);
				if (Character.isDigit(c) || c == '_') {
					checkable = false;
				}
				runEnd++;
			}
			int start = i;
			int end = runEnd;
			while (start < end && isApostrophe(text.charAt(start))) {
				start++;
			}
			while (end > start && isApostrophe(text.charAt(end - 1))) {
				end--;
			}
			if (checkable && end - start >= 2) {
				out.add(new WordSpan(start, end, text.substring(start, end)));
			}
			i = runEnd;
		}
	}

	private static boolean isRunChar(String text, int i) {
		char c = text.charAt(i);
		return Character.isLetterOrDigit(c) || c == '_' || isWordChar(text, i);
	}

	/** Letters, apostrophes and marks on letters, and a quote inside a Hebrew acronym (צה"ל). */
	private static boolean isWordChar(String text, int i) {
		char c = text.charAt(i);
		return Character.isLetter(c) || isApostrophe(c) || isMark(c)
			|| ((c == '"' || c == '\u05F4') && isHebrewLetter(text, i - 1) && isHebrewLetter(text, i + 1));
	}

	private static boolean isApostrophe(char c) {
		return c == '\'' || c == '’' || c == '\u05F3';
	}

	private static boolean isMark(char c) {
		int type = Character.getType(c);
		return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || c == '\u0640';
	}

	private static boolean isHebrewLetter(String text, int i) {
		return i >= 0 && i < text.length() && text.charAt(i) >= '\u05D0' && text.charAt(i) <= '\u05EA';
	}
}
