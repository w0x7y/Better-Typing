package com.bettertyping.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
	@Accessor("page")
	MultiLineEditBox bettertyping$page();

	@Accessor("currentPage")
	int bettertyping$currentPage();
}
