package com.bettertyping.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A keyboard shortcut such as {@code ctrl+shift+z}. The key is a lowercase name: a letter or digit
 * ({@code z}, {@code 7}) or a Minecraft key name without its prefix ({@code f5}, {@code tab}).
 * "ctrl" means Cmd on macOS, like vanilla's copy/paste shortcuts. A pressed key is a combo too, so
 * matching a shortcut is plain equality.
 */
public record KeyCombo(String key, boolean ctrl, boolean shift, boolean alt) {
	/** Keys the mod handles itself while a suggestion list can be open. */
	public static final String TAB = "tab";
	public static final String ESCAPE = "escape";
	/** Keys a shortcut can use. Tab, Space, Enter and Escape are left out: the mod and the game already use them. */
	public static final List<String> SHORTCUT_KEYS = shortcutKeys();

	public KeyCombo {
		key = key.toLowerCase(Locale.ROOT);
	}

	public static KeyCombo parse(String text) {
		boolean ctrl = false;
		boolean shift = false;
		boolean alt = false;
		String key = null;
		for (String part : text.toLowerCase(Locale.ROOT).split("\\+")) {
			switch (part.strip()) {
				case "ctrl", "cmd", "control" -> ctrl = true;
				case "shift" -> shift = true;
				case "alt", "option" -> alt = true;
				case "" -> {
				}
				default -> key = part.strip();
			}
		}
		if (key == null) {
			throw new IllegalArgumentException("No key in shortcut: " + text);
		}
		return new KeyCombo(key, ctrl, shift, alt);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		if (ctrl) {
			text.append("ctrl+");
		}
		if (shift) {
			text.append("shift+");
		}
		if (alt) {
			text.append("alt+");
		}
		return text.append(key).toString();
	}

	private static List<String> shortcutKeys() {
		List<String> keys = new ArrayList<>();
		for (char c = 'a'; c <= 'z'; c++) {
			keys.add(String.valueOf(c));
		}
		for (char c = '0'; c <= '9'; c++) {
			keys.add(String.valueOf(c));
		}
		for (int i = 1; i <= 12; i++) {
			keys.add("f" + i);
		}
		keys.addAll(List.of("backspace", "delete", "insert", "home", "end", "page.up", "page.down", "left", "right", "up", "down"));
		return List.copyOf(keys);
	}
}
