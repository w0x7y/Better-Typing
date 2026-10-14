package com.bettertyping.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Undo/redo for one text field, built from observed states rather than hooked edits.
 *
 * <p>Consecutive typing is grouped into one step per word (the word plus the spaces after it), and
 * consecutive single-character deletes are grouped too. A pause, a cursor jump, a paste or any other
 * edit starts a new step.
 */
public final class UndoHistory {
	static final int MAX_STEPS = 200;
	static final long GROUP_TIMEOUT_MS = 1_000;

	private enum Kind { TYPING, DELETING, OTHER }

	private final Deque<TextState> undo = new ArrayDeque<>();
	private final Deque<TextState> redo = new ArrayDeque<>();
	private TextState current;
	private Kind lastKind;
	private long lastTime;

	public void reset(TextState state) {
		undo.clear();
		redo.clear();
		current = state;
		lastKind = null;
	}

	public TextState current() {
		return current;
	}

	/** Records whatever changed since the last observed state. */
	public void observe(TextState now, long timeMs) {
		if (current == null) {
			reset(now);
			return;
		}
		if (now.equals(current)) {
			return;
		}
		if (now.text().equals(current.text())) {
			// Cursor moved: keep the newest cursor, but don't merge the next edit into the previous step.
			current = now;
			lastKind = null;
			return;
		}
		Kind kind = classify(current, now);
		boolean merge = kind != Kind.OTHER && kind == lastKind && timeMs - lastTime < GROUP_TIMEOUT_MS
			&& !(kind == Kind.TYPING && startsNewWord(current, now));
		if (!merge) {
			push(undo, current);
		}
		redo.clear();
		current = now;
		lastKind = kind;
		lastTime = timeMs;
	}

	/** Records an edit made by the mod itself (an accepted suggestion) as its own step. */
	public void recordEdit(TextState now) {
		if (current != null && !now.text().equals(current.text())) {
			push(undo, current);
			redo.clear();
		}
		current = now;
		lastKind = null;
	}

	public Optional<TextState> undo() {
		if (undo.isEmpty()) {
			return Optional.empty();
		}
		push(redo, current);
		current = undo.pop();
		lastKind = null;
		return Optional.of(current);
	}

	public Optional<TextState> redo() {
		if (redo.isEmpty()) {
			return Optional.empty();
		}
		push(undo, current);
		current = redo.pop();
		lastKind = null;
		return Optional.of(current);
	}

	private static void push(Deque<TextState> stack, TextState state) {
		stack.push(state);
		while (stack.size() > MAX_STEPS) {
			stack.removeLast();
		}
	}

	private static Kind classify(TextState before, TextState after) {
		if (before.hasSelection()) {
			return Kind.OTHER;
		}
		String a = before.text();
		String b = after.text();
		int pos = before.cursor();
		int added = b.length() - a.length();
		if (added >= 1 && added <= 2 && b.codePointCount(pos, pos + added) == 1
			&& b.startsWith(a.substring(0, pos)) && b.endsWith(a.substring(pos))) {
			return b.charAt(pos) == '\n' ? Kind.OTHER : Kind.TYPING;
		}
		int removed = -added;
		if (removed >= 1 && removed <= 2 && a.codePointCount(0, a.length()) - b.codePointCount(0, b.length()) == 1) {
			return Kind.DELETING;
		}
		return Kind.OTHER;
	}

	/** True when a non-space is typed right after a space, which starts a new undo step. */
	private static boolean startsNewWord(TextState before, TextState after) {
		int pos = before.cursor();
		if (pos == 0) {
			return false;
		}
		char previous = before.text().charAt(pos - 1);
		char typed = after.text().charAt(pos);
		return Character.isWhitespace(previous) && !Character.isWhitespace(typed);
	}
}
