package com.bettertyping.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface SignEditScreenAccessor {
	@Accessor("sign")
	SignBlockEntity bettertyping$sign();

	@Accessor("messages")
	String[] bettertyping$messages();

	@Accessor("line")
	int bettertyping$line();

	@Accessor("signField")
	TextFieldHelper bettertyping$signField();

	@Invoker("getSignYOffset")
	float bettertyping$signYOffset();
}
