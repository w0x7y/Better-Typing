package com.bettertyping.version;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

/** From 26.2 the open screen and the toasts live in Gui. */
public final class Screens {
	private Screens() {
	}

	public static Screen current(Minecraft client) {
		return client.gui.screen();
	}

	public static void open(Minecraft client, Screen screen) {
		client.gui.setScreen(screen);
	}

	public static ToastManager toasts(Minecraft client) {
		return client.gui.toastManager();
	}
}
