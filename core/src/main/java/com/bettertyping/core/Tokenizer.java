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
		if (pos < text.length() && isWordChar(text.charAt(pos))) {
			return null;
		}
		for (WordSpan word : words(text)) {
			if (word.end() == pos) {
				return word;
			}
		}
		return null;
	}

	/** Curly apostrophes count as straight ones for dictionary lookups. */
	public static String normalize(String word) {
		return word.replace('’', '\'');
	}

	static boolean isLinkLike(String chunk) {
		return chunk.contains("://") || chunk.startsWith("www.") || chunk.indexOf('@') >= 0
			|| chunk.startsWith("/") || DOMAIN.matcher(chunk).matches();
	}

	private static void collectRuns(String text, int from, int to, List<WordSpan> out) {
		int i = from;
		while (i < to) {
			if (!isRunChar(text.charAt(i))) {
				i++;
				continue;
			}
			int runEnd = i;
			boolean checkable = true;
			while (runEnd < to && isRunChar(text.charAt(runEnd))) {
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

	private static boolean isRunChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || isApostrophe(c);
	}

	private static boolean isWordChar(char c) {
		return Character.isLetter(c) || isApostrophe(c);
	}

	private static boolean isApostrophe(char c) {
		return c == '\'' || c == '’';
	}
}
