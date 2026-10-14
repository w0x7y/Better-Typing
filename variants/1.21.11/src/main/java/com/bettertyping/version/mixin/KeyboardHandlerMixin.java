package com.bettertyping.version.mixin;

import com.bettertyping.ScreenHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's screen API for 1.21.11 has no typed-character event, so Space is caught here. */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
	private void bettertyping$charTyped(long window, CharacterEvent event, CallbackInfo ci) {
		Screen screen = minecraft.screen;
		if (screen != null && window == minecraft.getWindow().handle() && ScreenHooks.onChar(screen, event.codepoint())) {
			ci.cancel();
		}
	}
}
