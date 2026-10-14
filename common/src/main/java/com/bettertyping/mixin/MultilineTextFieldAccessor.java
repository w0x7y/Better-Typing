package com.bettertyping.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
	@Accessor("font")
	Font bettertyping$font();

	@Accessor("width")
	int bettertyping$width();

	@Accessor("selectCursor")
	int bettertyping$selectCursor();
}
