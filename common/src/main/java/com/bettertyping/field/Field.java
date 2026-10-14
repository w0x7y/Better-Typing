package com.bettertyping.field;

import com.bettertyping.core.TextLayout;
import com.bettertyping.core.TypingField;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** One editable piece of text on screen: a text box, a sign line or a book page. */
public interface Field extends TypingField {
	/** Where the field draws its text right now, one entry per visible line. */
	List<TextLayout.Line> lines();

	/** Screen rectangles covering characters {@code [start, end)}; empty if they're scrolled out of view. */
	default List<TextLayout.Segment> segments(int start, int end) {
		Font font = Minecraft.getInstance().font;
		return TextLayout.segments(state().text(), lines(), start, end, font::width, font.lineHeight);
	}
}
