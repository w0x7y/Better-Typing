package com.bettertyping.version;

import com.bettertyping.version.signmixin.SignScaleInvoker;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.joml.Vector3fc;

/** The scale signs draw their text at. Returned as {@code Vector3fc} by this Minecraft version. */
public final class SignScale {
	private SignScale() {
	}

	public static Vector3fc of(AbstractSignEditScreen screen) {
		return ((SignScaleInvoker) screen).bettertyping$signTextScale();
	}
}
