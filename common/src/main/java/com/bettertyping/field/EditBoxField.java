package com.bettertyping.field;

import com.bettertyping.core.TextLayout;
import com.bettertyping.core.TextState;
import com.bettertyping.mixin.EditBoxAccessor;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

/** A single-line text box: chat, anvil rename, book title and every other {@link EditBox}. */
record EditBoxField(EditBox box, boolean assist) implements Field {
	@Override
	public Object key() {
		return box;
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

	/** The part of the text that fits in the box, from where it's scrolled to. */
	@Override
	public List<TextLayout.Line> lines() {
		EditBoxAccessor fields = (EditBoxAccessor) box;
		Font font = fields.bettertyping$font();
		String value = box.getValue();
		int displayPos = Math.min(fields.bettertyping$displayPos(), value.length());
		String shown = font.plainSubstrByWidth(value.substring(displayPos), box.getInnerWidth());
		return List.of(new TextLayout.Line(displayPos, displayPos + shown.length(), fields.bettertyping$textX(), fields.bettertyping$textY(), 1));
	}
}
