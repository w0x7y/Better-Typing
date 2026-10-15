package com.bettertyping.version;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** From 26.2 the toast manager lives in Gui, like the open screen. */
public final class Toasts {
	private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId();

	private Toasts() {
	}

	/** Shows a toast, replacing the mod's previous one if it's still up. */
	public static void show(Minecraft client, Component title, Component message) {
		SystemToast.addOrUpdate(client.gui.toastManager(), ID, title, message);
	}
}
