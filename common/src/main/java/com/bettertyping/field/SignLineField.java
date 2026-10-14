package com.bettertyping.field;

import com.bettertyping.core.TextState;
import com.bettertyping.core.TextTarget;
import com.bettertyping.mixin.SignEditScreenAccessor;
import com.bettertyping.version.SignScale;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.joml.Vector3fc;

/**
 * One of the four lines of a sign being edited. Only the current line has a cursor; the others are
 * still checked so their misspellings stay underlined.
 */
record SignLineField(AbstractSignEditScreen screen, int line) implements Field, TextTarget {
	private record Key(int line) {
	}

	@Override
	public Object key() {
		return new Key(line);
	}

	@Override
	public TextTarget target() {
		return this;
	}

	@Override
	public boolean assist() {
		return true;
	}

	private SignEditScreenAccessor fields() {
		return (SignEditScreenAccessor) screen;
	}

	private String text() {
		String text = fields().bettertyping$messages()[line];
		return text == null ? "" : text;
	}

	private boolean isCurrent() {
		return fields().bettertyping$line() == line;
	}

	@Override
	public TextState state() {
		String text = text();
		if (!isCurrent()) {
			return TextState.of(text, text.length());
		}
		TextFieldHelper helper = fields().bettertyping$signField();
		int cursor = Math.min(helper.getCursorPos(), text.length());
		return new TextState(text, cursor, Math.min(helper.getSelectionPos(), text.length()));
	}

	/** Only used on the current line. Text that doesn't fit the sign is rejected, as when typing. */
	@Override
	public void apply(TextState state) {
		TextFieldHelper helper = fields().bettertyping$signField();
		helper.selectAll();
		helper.insertText(state.text());
		helper.setSelectionRange(state.cursor(), state.anchor());
	}

	/** Mirrors how the sign screen centers each line, then translates and scales it. */
	@Override
	public List<Segment> segments(int start, int end) {
		Font font = Minecraft.getInstance().font;
		String text = text();
		start = Math.clamp(start, 0, text.length());
		end = Math.clamp(end, start, text.length());
		int lineHeight = fields().bettertyping$sign().getTextLineHeight();
		int lineLeft = -font.width(text) / 2;
		int lineTop = line * lineHeight - 4 * lineHeight / 2;
		Vector3fc scale = SignScale.of(screen);
		float originX = screen.width / 2.0F;
		float originY = fields().bettertyping$signYOffset();
		int x1 = Math.round(originX + (lineLeft + font.width(text.substring(0, start))) * scale.x());
		int x2 = Math.round(originX + (lineLeft + font.width(text.substring(0, end))) * scale.x());
		int top = Math.round(originY + lineTop * scale.y());
		int bottom = Math.round(originY + (lineTop + font.lineHeight - 1) * scale.y());
		return List.of(new Segment(x1, x2, top, bottom));
	}
}
