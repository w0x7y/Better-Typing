package com.bettertyping.field;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TextTarget;
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
record BookPageField(MultiLineEditBox box, int page) implements Field, TextTarget {
	private record Key(int page) {
	}

	@Override
	public Object key() {
		return new Key(page);
	}

	@Override
	public TextTarget target() {
		return this;
	}

	@Override
	public boolean assist() {
		return true;
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
		box.setValue(state.text(), true);
		MultilineTextField field = textField();
		field.setSelecting(false);
		field.seekCursor(Whence.ABSOLUTE, state.anchor());
		field.setSelecting(true);
		field.seekCursor(Whence.ABSOLUTE, state.cursor());
		field.setSelecting(false);
	}

	/** Wraps the text the same way the text box does, then maps each line to screen space. */
	@Override
	public List<Segment> segments(int start, int end) {
		MultilineTextField field = textField();
		MultilineTextFieldAccessor fieldData = (MultilineTextFieldAccessor) field;
		Font font = fieldData.bettertyping$font();
		String value = field.value();
		List<int[]> lines = new ArrayList<>();
		font.getSplitter().splitLines(value, fieldData.bettertyping$width(), Style.EMPTY, false,
			(style, lineStart, lineEnd) -> lines.add(new int[] {lineStart, lineEnd}));

		TextAreaWidgetInvoker area = (TextAreaWidgetInvoker) box;
		int left = area.bettertyping$innerLeft();
		int top = area.bettertyping$innerTop() - (int) box.scrollAmount();
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			int lineStart = lines.get(i)[0];
			int lineEnd = lines.get(i)[1];
			int from = Math.max(start, lineStart);
			int to = Math.min(end, lineEnd);
			if (from >= to) {
				continue;
			}
			int y = top + i * font.lineHeight;
			if (y < box.getY() || y + font.lineHeight > box.getY() + box.getHeight()) {
				continue;
			}
			int x1 = left + font.width(value.substring(lineStart, from));
			int x2 = left + font.width(value.substring(lineStart, to));
			segments.add(new Segment(x1, x2, y, y + font.lineHeight - 1));
		}
		return segments;
	}
}
