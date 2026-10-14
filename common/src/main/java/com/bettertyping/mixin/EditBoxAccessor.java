package com.bettertyping.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
	@Accessor("font")
	Font bettertyping$font();

	@Accessor("displayPos")
	int bettertyping$displayPos();

	@Accessor("highlightPos")
	int bettertyping$highlightPos();

	@Accessor("textX")
	int bettertyping$textX();

	@Accessor("textY")
	int bettertyping$textY();
}
