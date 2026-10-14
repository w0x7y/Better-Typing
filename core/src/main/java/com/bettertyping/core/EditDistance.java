package com.bettertyping.core;

/** Optimal string alignment distance: insertions, deletions, substitutions and adjacent swaps. */
public final class EditDistance {
	private EditDistance() {
	}

	/** The distance between {@code a} and {@code b}, or {@code max + 1} as soon as it must exceed {@code max}. */
	public static int bounded(String a, String b, int max) {
		int n = a.length();
		int m = b.length();
		if (Math.abs(n - m) > max) {
			return max + 1;
		}
		int[] prevPrev = new int[m + 1];
		int[] prev = new int[m + 1];
		int[] cur = new int[m + 1];
		for (int j = 0; j <= m; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= n; i++) {
			cur[0] = i;
			int rowMin = cur[0];
			char ca = a.charAt(i - 1);
			for (int j = 1; j <= m; j++) {
				char cb = b.charAt(j - 1);
				int cost = ca == cb ? 0 : 1;
				int value = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
				if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
					value = Math.min(value, prevPrev[j - 2] + 1);
				}
				cur[j] = value;
				rowMin = Math.min(rowMin, value);
			}
			if (rowMin > max) {
				return max + 1;
			}
			int[] tmp = prevPrev;
			prevPrev = prev;
			prev = cur;
			cur = tmp;
		}
		return Math.min(prev[m], max + 1);
	}
}
