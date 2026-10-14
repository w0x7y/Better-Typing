package com.bettertyping.field;

import com.bettertyping.mixin.AnvilScreenAccessor;
import com.bettertyping.mixin.BookEditScreenAccessor;
import com.bettertyping.mixin.BookSignScreenAccessor;
import com.bettertyping.mixin.ChatScreenAccessor;
import com.bettertyping.mixin.SignEditScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;

/**
 * The text fields of a screen: all of them (for underlines) and the one being typed in. This is the only
 * place that decides which fields get spell check and autocomplete.
 */
public record ScreenFields(List<Field> all, Field focused) {
	/** Null when the screen has no field to help with. */
	public static ScreenFields find(Screen screen) {
		if (screen instanceof AbstractSignEditScreen sign) {
			// Sign screens send every key to the current line, whatever widget has focus.
			List<Field> lines = new ArrayList<>(4);
			for (int i = 0; i < 4; i++) {
				lines.add(new SignLineField(sign, i, true));
			}
			return new ScreenFields(lines, lines.get(((SignEditScreenAccessor) sign).bettertyping$line()));
		}
		if (screen instanceof BookEditScreen book) {
			BookEditScreenAccessor fields = (BookEditScreenAccessor) book;
			MultiLineEditBox page = fields.bettertyping$page();
			if (page == null || !page.isFocused() || !page.visible) {
				return null;
			}
			Field field = new BookPageField(page, fields.bettertyping$currentPage(), true);
			return new ScreenFields(List.of(field), field);
		}
		if (screen.getFocused() instanceof EditBox box && box.canConsumeInput() && box.isVisible()) {
			Field field = new EditBoxField(box, isAssistBox(screen, box));
			return new ScreenFields(List.of(field), field);
		}
		return null;
	}

	/** Spell check and autocomplete: chat (except commands), anvil rename and book titles. */
	private static boolean isAssistBox(Screen screen, EditBox box) {
		if (screen instanceof ChatScreen chat && ((ChatScreenAccessor) chat).bettertyping$input() == box) {
			return !box.getValue().startsWith("/");
		}
		if (screen instanceof AnvilScreen anvil) {
			return ((AnvilScreenAccessor) anvil).bettertyping$name() == box;
		}
		return screen instanceof BookSignScreen bookSign && ((BookSignScreenAccessor) bookSign).bettertyping$titleBox() == box;
	}
}
