package com.bettertyping;

import com.bettertyping.core.KeyCombo;
import com.bettertyping.core.OverlayLayout;
import com.bettertyping.core.Popup;
import com.bettertyping.core.TypingSession;
import com.bettertyping.field.ScreenFields;
import com.bettertyping.version.KeyCodes;
import com.bettertyping.version.VersionBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;

/** Connects each screen's text fields to its {@link TypingSession}: input goes in, squiggles and the popup come out. */
public final class ScreenHooks {
	private static final int UNDERLINE_COLOR = 0xFFFF5555;
	// The popup copies vanilla's command suggestion list (CommandSuggestions.SuggestionsList).
	private static final int POPUP_BACKGROUND = 0xD0000000;
	private static final int TEXT = 0xFFAAAAAA;
	private static final int TEXT_HIGHLIGHT = 0xFFFFFF00;
	private static final String KEY_PREFIX = "key.keyboard.";

	private ScreenHooks() {
	}

	/** Called after every screen (re)initializes. Fabric clears a screen's hooks on each init, so they're registered again. */
	static void attach(Screen screen) {
		TypingScreen typing = (TypingScreen) screen;
		if (typing.bettertyping$session() == null) {
			typing.bettertyping$setSession(new TypingSession(BetterTyping.checker(), BetterTyping.settings()));
		}
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !onKey(s, event));
		VersionBridge.registerScreen(screen);
	}

	/** Returns true when the key was used and must not reach the screen. */
	public static boolean onKey(Screen screen, KeyEvent event) {
		ScreenFields fields = ScreenFields.find(screen);
		TypingSession session = ((TypingScreen) screen).bettertyping$session();
		if (fields == null || session == null) {
			return false;
		}
		KeyCombo pressed = new KeyCombo(keyName(event), event.hasControlDownWithQuirk(), event.hasShiftDown(), event.hasAltDown());
		return session.onKey(fields.focused(), pressed, System.currentTimeMillis());
	}

	/** Returns true when the character was used and must not be typed. */
	public static boolean onChar(Screen screen, int codepoint) {
		ScreenFields fields = ScreenFields.find(screen);
		TypingSession session = ((TypingScreen) screen).bettertyping$session();
		if (fields == null || session == null) {
			return false;
		}
		return session.onChar(fields.focused(), codepoint, System.currentTimeMillis());
	}

	/** Draws red squiggles under misspelled words and the suggestion popup, where {@link OverlayLayout} puts them. */
	public static void render(Screen screen, Canvas canvas) {
		ScreenFields fields = ScreenFields.find(screen);
		TypingSession session = ((TypingScreen) screen).bettertyping$session();
		if (fields == null || session == null) {
			return;
		}
		TypingSession.Overlay overlay = session.overlay(fields.all(), fields.focused(), System.currentTimeMillis());
		Font font = Minecraft.getInstance().font;
		OverlayLayout.Metrics metrics = new OverlayLayout.Metrics(screen.width, screen.height, font.lineHeight, font::width, ScreenHooks::label);
		OverlayLayout.Drawing drawing = OverlayLayout.layout(overlay, metrics);

		for (OverlayLayout.Squiggle squiggle : drawing.squiggles()) {
			for (int x = squiggle.x1(); x < squiggle.x2(); x++) {
				int y = squiggle.y() + squiggle.dy(x);
				canvas.fill(x, y, x + 1, y + 1, UNDERLINE_COLOR);
			}
		}
		OverlayLayout.SuggestionBox box = drawing.suggestions();
		if (box != null) {
			canvas.nextStratum();
			canvas.fill(box.x1(), box.y1(), box.x2(), box.y2(), POPUP_BACKGROUND);
			for (OverlayLayout.Row row : box.rows()) {
				canvas.text(font, row.text(), row.x(), row.y(), row.highlighted() ? TEXT_HIGHLIGHT : TEXT);
			}
		}
	}

	private static String label(Popup.Item item) {
		return item.addToDictionary() ? I18n.get("bettertyping.popup.add_to_dictionary", item.text()) : item.text();
	}

	/** Lowercase key name: a letter/digit as typed on the current layout, else Minecraft's key name without its prefix. */
	private static String keyName(KeyEvent event) {
		int c = KeyCodes.shortcutChar(event);
		if (c >= 0) {
			return String.valueOf((char) c);
		}
		String name = InputConstants.getKey(event).getName();
		return name.startsWith(KEY_PREFIX) ? name.substring(KEY_PREFIX.length()) : name;
	}
}
