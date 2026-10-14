package com.bettertyping;

import com.bettertyping.core.Popup;
import com.bettertyping.core.Settings;
import com.bettertyping.core.TextState;
import com.bettertyping.core.TypingAssistant;
import com.bettertyping.core.WordSpan;
import com.bettertyping.field.Field;
import com.bettertyping.field.ScreenFields;
import com.bettertyping.version.KeyCodes;
import com.bettertyping.version.VersionBridge;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;

/** Connects open screens to a {@link TypingAssistant} for each of their text fields. */
public final class ScreenHooks {
	private static final int UNDERLINE_COLOR = 0xFFFF5555;
	private static final int POPUP_BACKGROUND = 0xF0101018;
	private static final int POPUP_BORDER = 0xFF505064;
	private static final int ROW_HIGHLIGHT = 0xFF34345A;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_HIGHLIGHT = 0xFFFFFF55;
	private static final int TEXT_ADD = 0xFF9A9AAA;
	private static final int ROW_HEIGHT = 12;
	private static final int PADDING = 4;
	private static final String KEY_PREFIX = "key.keyboard.";

	private static final Map<Screen, ScreenState> SCREENS = new WeakHashMap<>();

	/** A screen's assistants by field key, and which field was typed in last. */
	private static final class ScreenState {
		final Map<Object, TypingAssistant> assistants = new HashMap<>();
		Object lastFocused;
	}

	private record Focus(Field field, TypingAssistant assistant) {
	}

	private ScreenHooks() {
	}

	static void attach(Screen screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !onKey(s, event));
		VersionBridge.registerScreen(screen);
	}

	private static ScreenState state(Screen screen) {
		return SCREENS.computeIfAbsent(screen, s -> new ScreenState());
	}

	private static TypingAssistant assistant(Screen screen, Field field) {
		return state(screen).assistants.computeIfAbsent(field.key(),
			key -> new TypingAssistant(BetterTyping.checker(), BetterTyping.settings()));
	}

	private static Focus focus(Screen screen, ScreenFields fields) {
		if (fields == null) {
			return null;
		}
		Field field = fields.focused();
		if (field.assist()) {
			GameWords.refreshIfNeeded(BetterTyping.checker());
		}
		// Moving to another field (sign line, book page) closes the popup left open in the old one.
		ScreenState state = state(screen);
		if (!Objects.equals(state.lastFocused, field.key())) {
			TypingAssistant previous = state.assistants.get(state.lastFocused);
			if (previous != null) {
				previous.onEscape();
			}
			state.lastFocused = field.key();
		}
		return new Focus(field, assistant(screen, field));
	}

	/** Returns true when the key was used and must not reach the screen. */
	public static boolean onKey(Screen screen, KeyEvent event) {
		Focus focus = focus(screen, ScreenFields.find(screen));
		if (focus == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		Settings settings = BetterTyping.settings();
		boolean ctrl = event.hasControlDownWithQuirk();
		boolean shift = event.hasShiftDown();
		boolean alt = event.hasAltDown();
		if (settings.undoRedo) {
			String key = keyName(event);
			if (settings.undoKey.matches(key, ctrl, shift, alt)) {
				return focus.assistant().undo(focus.field().target(), now);
			}
			if (settings.redoKey.matches(key, ctrl, shift, alt) || settings.redoAltKey.matches(key, ctrl, shift, alt)) {
				return focus.assistant().redo(focus.field().target(), now);
			}
		}
		if (event.isCycleFocus() && !ctrl && !shift && !alt) {
			return focus.assistant().onTab(focus.field().target(), focus.field().assist(), now);
		}
		if (event.isEscape()) {
			return focus.assistant().onEscape();
		}
		return false;
	}

	/** Returns true when the character was used and must not be typed. */
	public static boolean onChar(Screen screen, int codepoint) {
		if (codepoint != ' ') {
			return false;
		}
		Focus focus = focus(screen, ScreenFields.find(screen));
		if (focus == null) {
			return false;
		}
		try {
			return focus.assistant().onSpace(focus.field().target(), focus.field().assist(), System.currentTimeMillis());
		} catch (UncheckedIOException e) {
			BetterTyping.LOGGER.error("Couldn't save the personal dictionary", e);
			return true;
		}
	}

	/** Draws red squiggles under misspelled words and the suggestion popup. */
	public static void render(Screen screen, Canvas canvas) {
		ScreenFields fields = ScreenFields.find(screen);
		Focus focus = focus(screen, fields);
		if (focus == null) {
			return;
		}
		focus.assistant().sync(focus.field().target(), focus.field().assist(), System.currentTimeMillis());

		for (Field field : fields.all()) {
			if (!field.assist()) {
				continue;
			}
			TextState state = field.target().state();
			int cursor = field == focus.field() ? state.cursor() : -1;
			for (WordSpan word : assistant(screen, field).misspelled(state.text(), cursor, true)) {
				for (Field.Segment segment : field.segments(word.start(), word.end())) {
					squiggle(canvas, segment.x1(), segment.x2(), segment.bottom());
				}
			}
		}

		Popup popup = focus.assistant().popup();
		if (popup != null) {
			List<Field.Segment> anchor = focus.field().segments(popup.word().start(), popup.word().end());
			if (!anchor.isEmpty()) {
				drawPopup(canvas, Minecraft.getInstance().font, popup, anchor.get(0), screen.width, screen.height);
			}
		}
	}

	private static void squiggle(Canvas canvas, int x1, int x2, int y) {
		for (int x = x1; x < x2; x++) {
			int dy = ((x - x1) >> 1) & 1;
			canvas.fill(x, y + dy, x + 1, y + dy + 1, UNDERLINE_COLOR);
		}
	}

	/** Draws the popup above the word, or below it when there's no room above. */
	private static void drawPopup(Canvas canvas, Font font, Popup popup, Field.Segment word, int screenWidth, int screenHeight) {
		List<Popup.Item> items = popup.items();
		String[] labels = new String[items.size()];
		int width = 0;
		for (int i = 0; i < items.size(); i++) {
			Popup.Item item = items.get(i);
			labels[i] = item.addToDictionary() ? I18n.get("bettertyping.popup.add_to_dictionary", item.text()) : item.text();
			width = Math.max(width, font.width(labels[i]));
		}
		width += PADDING * 2;
		int height = items.size() * ROW_HEIGHT + 2;
		int x = Math.max(0, Math.min(word.x1() - PADDING, screenWidth - width));
		int y = word.top() - height - 3;
		if (y < 0) {
			y = Math.min(word.bottom() + 3, screenHeight - height);
		}

		canvas.nextStratum();
		canvas.fill(x, y, x + width, y + height, POPUP_BORDER);
		canvas.fill(x + 1, y + 1, x + width - 1, y + height - 1, POPUP_BACKGROUND);
		for (int i = 0; i < items.size(); i++) {
			int rowY = y + 1 + i * ROW_HEIGHT;
			boolean highlighted = i == popup.selected();
			if (highlighted) {
				canvas.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, ROW_HIGHLIGHT);
			}
			int color = highlighted ? TEXT_HIGHLIGHT : items.get(i).addToDictionary() ? TEXT_ADD : TEXT;
			canvas.text(font, labels[i], x + PADDING, rowY + 2, color);
		}
	}

	/** Lowercase key name for shortcuts: a letter/digit as typed on the current layout, else Minecraft's key name. */
	private static String keyName(KeyEvent event) {
		int c = KeyCodes.shortcutChar(event);
		if (c >= 0) {
			return String.valueOf((char) c);
		}
		String name = InputConstants.getKey(event).getName();
		return name.startsWith(KEY_PREFIX) ? name.substring(KEY_PREFIX.length()) : name;
	}
}
