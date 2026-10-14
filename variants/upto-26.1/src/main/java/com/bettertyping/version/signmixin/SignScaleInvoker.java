package com.bettertyping.version.signmixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface SignScaleInvoker {
	@Invoker("getSignTextScale")
	Vector3f bettertyping$signTextScale();
}
