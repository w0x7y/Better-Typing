package com.bettertyping.version;

import com.bettertyping.Canvas;
import com.bettertyping.ScreenHooks;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.x: GuiGraphicsExtractor rendering and Fabric's typed-character event. */
public final class VersionBridge {
	private VersionBridge() {
	}

	public static void registerScreen(Screen screen) {
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> ScreenHooks.render(s, canvas(graphics)));
		ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> !ScreenHooks.onChar(s, event.codepoint()));
	}

	private static Canvas canvas(GuiGraphicsExtractor graphics) {
		return new Canvas() {
			@Override
			public void fill(int x1, int y1, int x2, int y2, int color) {
				graphics.fill(x1, y1, x2, y2, color);
			}

			@Override
			public void text(Font font, String text, int x, int y, int color) {
				graphics.text(font, text, x, y, color, true);
			}

			@Override
			public void nextStratum() {
				graphics.nextStratum();
			}
		};
	}
}
