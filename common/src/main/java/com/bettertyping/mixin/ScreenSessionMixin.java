package com.bettertyping.mixin;

import com.bettertyping.TypingScreen;
import com.bettertyping.core.TypingSession;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Screen.class)
abstract class ScreenSessionMixin implements TypingScreen {
	@Unique
	private TypingSession bettertyping$session;

	@Override
	public TypingSession bettertyping$session() {
		return bettertyping$session;
	}

	@Override
	public void bettertyping$setSession(TypingSession session) {
		bettertyping$session = session;
	}
}
