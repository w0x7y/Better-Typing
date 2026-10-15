package com.bettertyping.version;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** 1.21.11 and 26.1 get the toast manager with getToastManager(). */
public final class Toasts {
	private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId();

	private Toasts() {
	}

	/** Shows a toast, replacing the mod's previous one if it's still up. */
	public static void show(Minecraft client, Component title, Component message) {
		SystemToast.addOrUpdate(client.getToastManager(), ID, title, message);
	}
}
