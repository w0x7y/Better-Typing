package com.bettertyping.version;

import com.bettertyping.Canvas;
import com.bettertyping.ScreenHooks;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 1.21.11: GuiGraphics rendering. Typed characters come from {@code KeyboardHandlerMixin}. */
public final class VersionBridge {
	private VersionBridge() {
	}

	public static void registerScreen(Screen screen) {
		ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> ScreenHooks.render(s, canvas(graphics)));
	}

	private static Canvas canvas(GuiGraphics graphics) {
		return new Canvas() {
			@Override
			public void fill(int x1, int y1, int x2, int y2, int color) {
				graphics.fill(x1, y1, x2, y2, color);
			}

			@Override
			public void text(Font font, String text, int x, int y, int color) {
				graphics.drawString(font, text, x, y, color, false);
			}

			@Override
			public void nextStratum() {
				graphics.nextStratum();
			}
		};
	}
}
