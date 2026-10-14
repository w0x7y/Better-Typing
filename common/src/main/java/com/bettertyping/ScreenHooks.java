package com.bettertyping;

import com.bettertyping.core.KeyCombo;
import com.bettertyping.core.Popup;
import com.bettertyping.core.PopupPlacement;
import com.bettertyping.core.TextLayout;
import com.bettertyping.core.TypingSession;
import com.bettertyping.field.Field;
import com.bettertyping.field.ScreenFields;
import com.bettertyping.version.KeyCodes;
import com.bettertyping.version.VersionBridge;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
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
	private static final int ROW_HEIGHT = 12;
	private static final String KEY_PREFIX = "key.keyboard.";

	private ScreenHooks() {
	}

	/** Called after every screen (re)initializes. Fabric clears a screen's hooks on each init, so they're registered again. */
	static void attach(Screen screen) {
		TypingScreen typing = (TypingScreen) screen;
		if (typing.bettertyping$session() == null) {
			typing.bettertyping$setSession(new TypingSession(BetterTyping.checker(), BetterTyping.settings()));
		}
		// Item and mob names exist (and are translated) once a world is loaded; the language can only change between screens.
		if (Minecraft.getInstance().level != null) {
			GameWords.refreshIfNeeded(BetterTyping.checker());
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
		try {
			return session.onChar(fields.focused(), codepoint, System.currentTimeMillis());
		} catch (UncheckedIOException e) {
			BetterTyping.LOGGER.error("Couldn't save the personal dictionary", e);
			return true;
		}
	}

	/** Draws red squiggles under misspelled words and the suggestion popup. */
	public static void render(Screen screen, Canvas canvas) {
		ScreenFields fields = ScreenFields.find(screen);
		TypingSession session = ((TypingScreen) screen).bettertyping$session();
		if (fields == null || session == null) {
			return;
		}
		TypingSession.Overlay<Field> overlay = session.overlay(fields.all(), fields.focused(), System.currentTimeMillis());
		for (TypingSession.Misspelling<Field> misspelling : overlay.misspellings()) {
			for (TextLayout.Segment segment : misspelling.field().segments(misspelling.word().start(), misspelling.word().end())) {
				squiggle(canvas, segment.x1(), segment.x2(), segment.bottom());
			}
		}
		Popup popup = overlay.popup();
		if (popup != null) {
			List<TextLayout.Segment> word = fields.focused().segments(popup.word().start(), popup.word().end());
			if (!word.isEmpty()) {
				drawPopup(canvas, Minecraft.getInstance().font, popup, word.get(0), text(fields), screen.width, screen.height);
			}
		}
	}

	/** Where the visible text the popup should stay off is. */
	private static List<TextLayout.Segment> text(ScreenFields fields) {
		List<TextLayout.Segment> text = new ArrayList<>();
		for (Field field : fields.all()) {
			if (field.keepClear()) {
				text.addAll(field.segments(0, field.state().text().length()));
			}
		}
		return text;
	}

	private static void squiggle(Canvas canvas, int x1, int x2, int y) {
		for (int x = x1; x < x2; x++) {
			int dy = ((x - x1) >> 1) & 1;
			canvas.fill(x, y + dy, x + 1, y + dy + 1, UNDERLINE_COLOR);
		}
	}

	/** Draws the popup next to the word, off the text around it (see {@link PopupPlacement}). */
	private static void drawPopup(Canvas canvas, Font font, Popup popup, TextLayout.Segment word, List<TextLayout.Segment> text,
		int screenWidth, int screenHeight) {
		List<Popup.Item> items = popup.items();
		String[] labels = new String[items.size()];
		int width = 0;
		for (int i = 0; i < items.size(); i++) {
			Popup.Item item = items.get(i);
			labels[i] = item.addToDictionary() ? I18n.get("bettertyping.popup.add_to_dictionary", item.text()) : item.text();
			width = Math.max(width, font.width(labels[i]));
		}
		width += 1;
		int height = items.size() * ROW_HEIGHT;
		PopupPlacement.Position position = PopupPlacement.place(word, width, height, text, screenWidth, screenHeight);
		int x = position.x();
		int y = position.y();

		canvas.nextStratum();
		canvas.fill(x, y, x + width, y + height, POPUP_BACKGROUND);
		for (int i = 0; i < items.size(); i++) {
			int rowY = y + i * ROW_HEIGHT;
			canvas.text(font, labels[i], x + 1, rowY + 2, i == popup.selected() ? TEXT_HIGHLIGHT : TEXT);
		}
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
