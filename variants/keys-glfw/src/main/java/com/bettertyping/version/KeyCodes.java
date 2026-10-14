package com.bettertyping.version;

import net.minecraft.client.input.KeyEvent;

/** Minecraft 1.21.11 to 26.2: GLFW key codes. */
public final class KeyCodes {
	private KeyCodes() {
	}

	/** The letter or digit of a shortcut key (as vanilla uses for copy/paste), or -1. */
	public static int shortcutChar(KeyEvent event) {
		int key = event.key();
		if (key >= 'A' && key <= 'Z') {
			return Character.toLowerCase(key);
		}
		return key >= '0' && key <= '9' ? key : -1;
	}
}
