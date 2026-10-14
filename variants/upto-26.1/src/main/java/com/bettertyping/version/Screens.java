package com.bettertyping.version;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 1.21.11 and 26.1 keep the open screen in a field. */
public final class Screens {
	private Screens() {
	}

	public static Screen current(Minecraft client) {
		return client.screen;
	}

	public static void open(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}
}
