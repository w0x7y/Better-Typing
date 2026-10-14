package com.bettertyping;

import com.bettertyping.core.TypingSession;

/** Every screen carries its own typing session (added by {@code ScreenSessionMixin}), so it lives exactly as long as the screen. */
public interface TypingScreen {
	TypingSession bettertyping$session();

	void bettertyping$setSession(TypingSession session);
}
