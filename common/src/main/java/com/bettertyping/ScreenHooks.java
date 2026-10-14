package com.bettertyping;

import com.bettertyping.core.Popup;
import com.bettertyping.core.Settings;
import com.bettertyping.core.TypingAssistant;
import com.bettertyping.core.WordSpan;
import com.bettertyping.mixin.AnvilScreenAccessor;
import com.bettertyping.mixin.ChatScreenAccessor;
import com.bettertyping.mixin.EditBoxAccessor;
import com.bettertyping.version.KeyCodes;
import com.bettertyping.version.VersionBridge;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;

/** Connects open screens to a {@link TypingAssistant} for their focused text field. */
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

	private static final Map<EditBox, TypingAssistant> ASSISTANTS = new WeakHashMap<>();

	private ScreenHooks() {
	}

	/** The focused field, its assistant, and whether spell check/autocomplete apply to it. */
	private record Focus(EditBox box, EditBoxTarget target, TypingAssistant assistant, boolean assist) {
	}

	static void attach(Screen screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !onKey(s, event));
		VersionBridge.registerScreen(screen);
	}

	private static Focus focus(Screen screen) {
		if (!(screen.getFocused() instanceof EditBox box) || !box.canConsumeInput()) {
			return null;
		}
		boolean assist = isAssistField(screen, box);
		if (assist) {
			GameWords.refreshIfNeeded(BetterTyping.checker());
		}
		TypingAssistant assistant = ASSISTANTS.computeIfAbsent(box,
			b -> new TypingAssistant(BetterTyping.checker(), BetterTyping.settings()));
		return new Focus(box, new EditBoxTarget(box), assistant, assist);
	}

	/** Spell check and autocomplete run in chat (except commands) and the anvil name field. */
	private static boolean isAssistField(Screen screen, EditBox box) {
		if (screen instanceof ChatScreen chat && ((ChatScreenAccessor) chat).bettertyping$input() == box) {
			return !box.getValue().startsWith("/");
		}
		return screen instanceof AnvilScreen anvil && ((AnvilScreenAccessor) anvil).bettertyping$name() == box;
	}

	/** Returns true when the key was used and must not reach the screen. */
	static boolean onKey(Screen screen, KeyEvent event) {
		Focus focus = focus(screen);
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
				return focus.assistant().undo(focus.target(), now);
			}
			if (settings.redoKey.matches(key, ctrl, shift, alt) || settings.redoAltKey.matches(key, ctrl, shift, alt)) {
				return focus.assistant().redo(focus.target(), now);
			}
		}
		if (event.isCycleFocus() && !ctrl && !shift && !alt) {
			return focus.assistant().onTab(focus.target(), focus.assist(), now);
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
		Focus focus = focus(screen);
		if (focus == null) {
			return false;
		}
		try {
			return focus.assistant().onSpace(focus.target(), focus.assist(), System.currentTimeMillis());
		} catch (UncheckedIOException e) {
			BetterTyping.LOGGER.error("Couldn't save the personal dictionary", e);
			return true;
		}
	}

	/** Draws red squiggles under misspelled words and the suggestion popup. */
	public static void render(Screen screen, Canvas canvas) {
		Focus focus = focus(screen);
		if (focus == null || !focus.box().isVisible()) {
			return;
		}
		TypingAssistant assistant = focus.assistant();
		assistant.sync(focus.target(), focus.assist(), System.currentTimeMillis());

		EditBox box = focus.box();
		EditBoxAccessor fields = (EditBoxAccessor) box;
		Font font = fields.bettertyping$font();
		String value = box.getValue();
		int displayPos = Math.min(fields.bettertyping$displayPos(), value.length());
		int textX = fields.bettertyping$textX();
		int textY = fields.bettertyping$textY();
		String shown = font.plainSubstrByWidth(value.substring(displayPos), box.getInnerWidth());
		int shownEnd = displayPos + shown.length();

		List<WordSpan> misspelled = assistant.misspelled(value, box.getCursorPosition(), focus.assist());
		for (WordSpan word : misspelled) {
			int start = Math.max(word.start(), displayPos);
			int end = Math.min(word.end(), shownEnd);
			if (start < end) {
				int x1 = textX + font.width(value.substring(displayPos, start));
				int x2 = textX + font.width(value.substring(displayPos, end));
				squiggle(canvas, x1, x2, textY + font.lineHeight);
			}
		}

		Popup popup = assistant.popup();
		if (popup != null) {
			int wordStart = Math.max(popup.word().start(), displayPos);
			int anchorX = textX + font.width(value.substring(displayPos, Math.min(wordStart, shownEnd)));
			drawPopup(canvas, font, popup, anchorX, box.getY(), box.getY() + box.getHeight(), screen.width, screen.height);
		}
	}

	private static void squiggle(Canvas canvas, int x1, int x2, int y) {
		for (int x = x1; x < x2; x++) {
			int dy = ((x - x1) >> 1) & 1;
			canvas.fill(x, y + dy, x + 1, y + dy + 1, UNDERLINE_COLOR);
		}
	}

	private static void drawPopup(Canvas canvas, Font font, Popup popup, int anchorX, int fieldTop, int fieldBottom,
		int screenWidth, int screenHeight) {
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
		int x = Math.max(0, Math.min(anchorX - PADDING, screenWidth - width));
		int y = fieldTop - height - 2;
		if (y < 0) {
			y = Math.min(fieldBottom + 2, screenHeight - height);
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
