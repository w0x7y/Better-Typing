package com.bettertyping.version;

import net.minecraft.client.input.KeyEvent;

/** Minecraft 26.3+: SDL key codes, where shortcut keys follow the keyboard layout. */
public final class KeyCodes {
	private KeyCodes() {
	}

	/** The layout-aware letter or digit of a shortcut key (as vanilla uses for copy/paste), or -1. */
	public static int shortcutChar(KeyEvent event) {
		int key = event.shortcutKey();
		return (key >= 'a' && key <= 'z') || (key >= '0' && key <= '9') ? key : -1;
	}
}
