package com.bettertyping.field;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TypingField;
import com.bettertyping.mixin.MultiLineEditBoxAccessor;
import com.bettertyping.mixin.MultilineTextFieldAccessor;
import com.bettertyping.mixin.TextAreaWidgetInvoker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Style;

/**
 * A page of a book and quill. The book screen reuses one text box for every page, so the page number
 * is part of the key: each page gets its own undo history.
 */
record BookPageField(MultiLineEditBox box, int page, boolean assist) implements TypingField {
	private record Key(int page) {
	}

	@Override
	public Object key() {
		return new Key(page);
	}

	private MultilineTextField textField() {
		return ((MultiLineEditBoxAccessor) box).bettertyping$textField();
	}

	@Override
	public TextState state() {
		MultilineTextField field = textField();
		return new TextState(field.value(), field.cursor(), ((MultilineTextFieldAccessor) field).bettertyping$selectCursor());
	}

	@Override
	public void apply(TextState state) {
		// Like typing, refuses text that runs past the page's last line.
		box.setValue(state.text(), false);
		MultilineTextField field = textField();
		if (!field.value().equals(state.text())) {
			return;
		}
		field.setSelecting(false);
		field.seekCursor(Whence.ABSOLUTE, state.anchor());
		field.setSelecting(true);
		field.seekCursor(Whence.ABSOLUTE, state.cursor());
		field.setSelecting(false);
	}

	@Override
	public boolean keepClear() {
		return false;
	}

	/** Wraps the text the same way the text box does, keeping the lines that aren't scrolled out of view. */
	@Override
	public List<Line> lines() {
		MultilineTextField field = textField();
		MultilineTextFieldAccessor fieldData = (MultilineTextFieldAccessor) field;
		Font font = fieldData.bettertyping$font();
		List<int[]> wrapped = new ArrayList<>();
		font.getSplitter().splitLines(field.value(), fieldData.bettertyping$width(), Style.EMPTY, false,
			(style, lineStart, lineEnd) -> wrapped.add(new int[] {lineStart, lineEnd}));

		TextAreaWidgetInvoker area = (TextAreaWidgetInvoker) box;
		int left = area.bettertyping$innerLeft();
		int top = area.bettertyping$innerTop() - (int) box.scrollAmount();
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < wrapped.size(); i++) {
			int y = top + i * font.lineHeight;
			if (y >= box.getY() && y + font.lineHeight <= box.getY() + box.getHeight()) {
				lines.add(new Line(wrapped.get(i)[0], wrapped.get(i)[1], left, y, 1));
			}
		}
		return lines;
	}

	@Override
	public int textWidth(String text) {
		return ((MultilineTextFieldAccessor) textField()).bettertyping$font().width(text);
	}
}
