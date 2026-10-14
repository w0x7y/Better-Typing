package com.bettertyping.field;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TextTarget;
import com.bettertyping.mixin.EditBoxAccessor;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

/** A single-line text box: chat, anvil rename, book title and every other {@link EditBox}. */
record EditBoxField(EditBox box, boolean assist) implements Field, TextTarget {
	@Override
	public Object key() {
		return box;
	}

	@Override
	public TextTarget target() {
		return this;
	}

	@Override
	public TextState state() {
		return new TextState(box.getValue(), box.getCursorPosition(), ((EditBoxAccessor) box).bettertyping$highlightPos());
	}

	@Override
	public void apply(TextState state) {
		box.setValue(state.text());
		box.moveCursorTo(state.anchor(), false);
		box.moveCursorTo(state.cursor(), true);
	}

	@Override
	public List<Segment> segments(int start, int end) {
		EditBoxAccessor fields = (EditBoxAccessor) box;
		Font font = fields.bettertyping$font();
		String value = box.getValue();
		int displayPos = Math.min(fields.bettertyping$displayPos(), value.length());
		String shown = font.plainSubstrByWidth(value.substring(displayPos), box.getInnerWidth());
		int from = Math.max(start, displayPos);
		int to = Math.min(end, displayPos + shown.length());
		if (from >= to) {
			return List.of();
		}
		int textX = fields.bettertyping$textX();
		int textY = fields.bettertyping$textY();
		int x1 = textX + font.width(value.substring(displayPos, from));
		int x2 = textX + font.width(value.substring(displayPos, to));
		return List.of(new Segment(x1, x2, textY, textY + font.lineHeight - 1));
	}
}
