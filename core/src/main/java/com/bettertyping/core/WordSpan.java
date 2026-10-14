package com.bettertyping.core;

/** A word inside a text, from {@code start} (inclusive) to {@code end} (exclusive). */
public record WordSpan(int start, int end, String text) {
	public int length() {
		return end - start;
	}

	public boolean contains(int pos) {
		return pos >= start && pos <= end;
	}
}
