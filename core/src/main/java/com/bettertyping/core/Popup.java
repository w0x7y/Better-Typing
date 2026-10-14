package com.bettertyping.core;

import java.util.List;

/** The suggestion list shown next to a word in a text field; {@link PopupPlacement} decides where. */
public final class Popup {
	public enum Mode { AUTOCOMPLETE, SPELLING }

	/** One row. {@code addToDictionary} rows add {@link #word()} to the personal dictionary instead of replacing it. */
	public record Item(String text, boolean addToDictionary) {
	}

	private final Mode mode;
	private final WordSpan word;
	private final List<Item> items;
	private int selected;

	Popup(Mode mode, WordSpan word, List<Item> items, int selected) {
		this.mode = mode;
		this.word = word;
		this.items = List.copyOf(items);
		this.selected = selected;
	}

	public Mode mode() {
		return mode;
	}

	/** The word the popup is for. */
	public WordSpan word() {
		return word;
	}

	public List<Item> items() {
		return items;
	}

	/** Index of the highlighted row, or -1 when nothing is highlighted yet. */
	public int selected() {
		return selected;
	}

	void selectNext() {
		selected = (selected + 1) % items.size();
	}
}
