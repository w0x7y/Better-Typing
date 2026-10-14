package com.bettertyping.mixin;

import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractTextAreaWidget.class)
public interface TextAreaWidgetInvoker {
	@Invoker("getInnerLeft")
	int bettertyping$innerLeft();

	@Invoker("getInnerTop")
	int bettertyping$innerTop();
}
