package com.bettertyping.version;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

/** 1.21.11 and 26.1 keep the open screen and the toasts in Minecraft. */
public final class Screens {
	private Screens() {
	}

	public static Screen current(Minecraft client) {
		return client.screen;
	}

	public static void open(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}

	public static ToastManager toasts(Minecraft client) {
		return client.getToastManager();
	}
}
