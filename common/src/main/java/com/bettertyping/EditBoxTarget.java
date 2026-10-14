package com.bettertyping;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TextTarget;
import com.bettertyping.mixin.EditBoxAccessor;
import net.minecraft.client.gui.components.EditBox;

/** A single-line text field: chat, anvil rename and every other {@link EditBox}. */
record EditBoxTarget(EditBox box) implements TextTarget {
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
}
