package com.bettertyping.field;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TypingField;
import com.bettertyping.mixin.SignEditScreenAccessor;
import com.bettertyping.version.signmixin.SignScaleInvoker;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.joml.Vector3fc;

/**
 * One of the four lines of a sign being edited. Only the current line has a cursor; the others are
 * still checked so their misspellings stay underlined.
 */
record SignLineField(AbstractSignEditScreen screen, int line, boolean assist) implements TypingField {
	private record Key(int line) {
	}

	@Override
	public Object key() {
		return new Key(line);
	}

	private SignEditScreenAccessor fields() {
		return (SignEditScreenAccessor) screen;
	}

	private String text() {
		String text = fields().bettertyping$messages()[line];
		return text == null ? "" : text;
	}

	@Override
	public TextState state() {
		String text = text();
		if (fields().bettertyping$line() != line) {
			return TextState.of(text, text.length());
		}
		TextFieldHelper helper = fields().bettertyping$signField();
		return new TextState(text, Math.min(helper.getCursorPos(), text.length()), Math.min(helper.getSelectionPos(), text.length()));
	}

	/** Only used on the current line. Text that doesn't fit the sign is refused, as when typing. */
	@Override
	public void apply(TextState state) {
		TextFieldHelper helper = fields().bettertyping$signField();
		helper.selectAll();
		helper.insertText(state.text());
		helper.setSelectionRange(state.cursor(), state.anchor());
	}

	/** Mirrors how the sign screen centers each line, then moves and scales it onto the sign. */
	@Override
	public List<Line> lines() {
		String text = text();
		int lineHeight = fields().bettertyping$sign().getTextLineHeight();
		int lineLeft = -textWidth(text) / 2;
		int lineTop = line * lineHeight - 4 * lineHeight / 2;
		// Signs scale text equally in x and y. The scale's type differs between versions (Vector3f / Vector3fc).
		Vector3fc scale = ((SignScaleInvoker) screen).bettertyping$signTextScale();
		float x = screen.width / 2.0F + lineLeft * scale.x();
		float y = fields().bettertyping$signYOffset() + lineTop * scale.y();
		return List.of(new Line(0, text.length(), x, y, scale.x()));
	}

	/** The sign screen draws with the game's font. */
	@Override
	public int textWidth(String text) {
		return Minecraft.getInstance().font.width(text);
	}
}
