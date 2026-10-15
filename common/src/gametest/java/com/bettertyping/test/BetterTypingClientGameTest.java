package com.bettertyping.test;

import com.bettertyping.BetterTyping;
import com.bettertyping.ScreenHooks;
import com.bettertyping.core.KeyCombo;
import com.bettertyping.mixin.BookEditScreenAccessor;
import com.bettertyping.mixin.ChatScreenAccessor;
import com.bettertyping.mixin.MultiLineEditBoxAccessor;
import com.bettertyping.mixin.SignEditScreenAccessor;
import com.bettertyping.version.Screens;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.gui.YACLScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Plays through chat, a sign and a book in a fresh world, checking the text after each step and
 * saving screenshots of the underlines and popups, then opens the settings screen.
 */
public class BetterTypingClientGameTest implements FabricClientGameTest {
	private static final String TEST_WORD = "blorptest";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			context.waitFor(client -> BetterTyping.checker().isReady());
			chat(context);
			sign(context, world.getServer());
			book(context, world.getServer());
			settings(context);
		}
	}

	private static void chat(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);

		type(context, "teh wrold th");
		context.takeScreenshot("bettertyping-01-chat-typing");
		press(context, "tab");
		context.takeScreenshot("bettertyping-02-chat-autocomplete-highlighted");
		type(context, " ");
		expect("teh wrold the ", chatText(context), "Tab + Space accepts the first completion");

		context.runOnClient(client -> chatInput(Screens.current(client)).moveCursorTo(6, false));
		press(context, "tab");
		context.takeScreenshot("bettertyping-03-chat-spelling");
		type(context, " ");
		expect("teh world the ", chatText(context), "Space accepts the first spelling suggestion");

		ctrl(context, "z");
		expect("teh wrold the ", chatText(context), "Ctrl+Z undoes the correction");
		ctrl(context, "y");
		expect("teh world the ", chatText(context), "Ctrl+Y redoes it");
		ctrl(context, "z");
		ctrlShift(context, "z");
		expect("teh world the ", chatText(context), "Ctrl+Shift+Z redoes too");

		// Resizing the window rebuilds the chat box.
		context.runOnClient(client -> Screens.current(client).resize(Screens.current(client).width, Screens.current(client).height));
		ctrl(context, "z");
		expect("teh wrold the ", chatText(context), "Undo history survives a resize");

		// The test word has no near matches, so its popup only holds "Add to dictionary".
		context.runOnClient(client -> chatInput(Screens.current(client)).setValue(TEST_WORD));
		context.runOnClient(client -> chatInput(Screens.current(client)).moveCursorTo(2, false));
		press(context, "tab");
		context.takeScreenshot("bettertyping-04-chat-add-to-dictionary");
		type(context, " ");
		check(BetterTyping.checker().isCorrect(TEST_WORD), "Add to dictionary makes the word correct");
		expect(TEST_WORD, chatText(context), "Adding a word leaves the text alone");
		removePersonalWord(TEST_WORD);

		context.runOnClient(client -> chatInput(Screens.current(client)).setValue(""));
		type(context, "/gamemode cr");
		press(context, "tab");
		context.takeScreenshot("bettertyping-05-chat-command");
		check(!chatText(context).contains("  "), "Commands keep vanilla Tab completion");

		context.runOnClient(client -> chatInput(Screens.current(client)).setValue(""));
		type(context, "th");
		press(context, "escape");
		check(context.computeOnClient(client -> Screens.current(client) instanceof ChatScreen), "First Escape only closes the popup");
		close(context);
	}

	private static void sign(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		server.runCommand("fill -2 99 -2 2 99 4 stone");
		server.runCommand("fill -2 100 -2 2 104 4 air");
		// A two-block wall straight ahead at eye height; /tp sets the facing (older test APIs lack lookAt).
		server.runCommand("fill 0 100 2 0 101 2 stone");
		server.runCommand("tp @a 0.5 100 0.5 0 0");
		server.runCommand("item replace entity @a weapon.mainhand with oak_sign");
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(AbstractSignEditScreen.class);

		type(context, "teh wrold");
		context.takeScreenshot("bettertyping-06-sign-typing");
		press(context, "tab");
		context.takeScreenshot("bettertyping-07-sign-spelling");
		type(context, " ");
		expect("teh world", signLine(context, 0), "Spelling suggestion on a sign line");

		press(context, "down");
		type(context, "hel");
		press(context, "tab");
		context.takeScreenshot("bettertyping-08-sign-autocomplete");
		type(context, " ");
		expect("hello ", signLine(context, 1), "Autocomplete on a sign line puts chat words first");
		ctrl(context, "z");
		expect("hel", signLine(context, 1), "Undo on a sign line");
		context.takeScreenshot("bettertyping-09-sign-after-undo");

		close(context);
	}

	private static void book(ClientGameTestContext context, TestServerContext server) {
		// Look at the sky, so using the book doesn't click the sign instead.
		server.runCommand("tp @a 0.5 100 0.5 0 -90");
		server.runCommand("item replace entity @a weapon.mainhand with writable_book");
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(BookEditScreen.class);

		type(context, "Ths is a tset of the bok with somee more wrods so the text wraps arond the page");
		context.takeScreenshot("bettertyping-10-book-typing");

		context.runOnClient(client -> ((MultiLineEditBoxAccessor) bookPage(Screens.current(client))).bettertyping$textField()
			.seekCursor(Whence.ABSOLUTE, 1));
		press(context, "tab");
		context.takeScreenshot("bettertyping-11-book-spelling");
		type(context, " ");
		String firstWord = bookText(context).split(" ")[0];
		check(!firstWord.equals("Ths") && BetterTyping.checker().isCorrect(firstWord) && bookText(context).endsWith(" is a tset of the bok"
			+ " with somee more wrods so the text wraps arond the page"), "Spelling suggestion in a book, got '" + bookText(context) + "'");
		ctrl(context, "z");
		check(bookText(context).startsWith("Ths is a tset"), "Undo in a book");

		close(context);
	}

	@SuppressWarnings("unchecked")
	private static void settings(ClientGameTestContext context) {
		for (String key : KeyCombo.SHORTCUT_KEYS) {
			check(isKeyName(key), "Shortcut key " + key + " is a Minecraft key name");
		}
		BetterTyping.checker().addPersonalWord(TEST_WORD);
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		type(context, "/bettertyping");
		press(context, "enter");
		context.waitForScreen(YACLScreen.class);
		check(true, "/bettertyping opens the settings screen");
		context.takeScreenshot("bettertyping-12-settings");

		// Turn autocomplete off the way the screen does: set the option, then press Save.
		context.runOnClient(client -> {
			YACLScreen screen = (YACLScreen) Screens.current(client);
			((Option<Boolean>) screen.config.categories().get(0).groups().get(0).options().get(1)).requestSet(false);
			screen.finishOrSave();
		});
		check(!BetterTyping.settings().autocomplete, "Saving the screen applies the change");
		check(settingsFile().contains("autocomplete=false"), "Saving the screen writes the settings file");

		context.runOnClient(client -> ((YACLScreen) Screens.current(client)).tabNavigationBar.selectTab(1, false));
		context.waitTicks(2);
		context.takeScreenshot("bettertyping-13-settings-dictionary");
		context.runOnClient(client -> {
			YACLScreen screen = (YACLScreen) Screens.current(client);
			ListOption<String> words = (ListOption<String>) screen.config.categories().get(1).groups().get(1);
			check(words.pendingValue().contains(TEST_WORD), "The personal dictionary lists added words");
			words.requestSet(List.of());
			screen.finishOrSave();
		});
		check(!BetterTyping.checker().isCorrect(TEST_WORD), "Removing a word from the list removes it from the dictionary");

		BetterTyping.settings().autocomplete = true;
		BetterTyping.saveSettings();
		close(context);
	}

	private static void removePersonalWord(String word) {
		List<String> words = new ArrayList<>(BetterTyping.checker().personalWords());
		words.remove(word);
		BetterTyping.checker().setPersonalWords(words);
	}

	/** Whether {@code key} is how the mod names a Minecraft key: its name without "key.keyboard.". */
	private static boolean isKeyName(String key) {
		try {
			return InputConstants.getKey("key.keyboard." + key).getName().equals("key.keyboard." + key);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static String settingsFile() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve("bettertyping/settings.properties"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Escape closes an open popup first, so it may take two presses to leave the screen. */
	private static void close(ClientGameTestContext context) {
		for (int i = 0; i < 2 && context.computeOnClient(client -> Screens.current(client) != null); i++) {
			press(context, "escape");
		}
		context.waitFor(client -> Screens.current(client) == null);
	}

	private static void type(ClientGameTestContext context, String text) {
		context.getInput().typeChars(text);
		context.waitTicks(2);
	}

	private static void press(ClientGameTestContext context, String key) {
		context.getInput().pressKey(InputConstants.getKey("key.keyboard." + key));
		context.waitTicks(2);
	}

	/**
	 * Sends Ctrl(+Shift)+key straight to the mod's key handler. Fabric's test input on 26.3 presses Ctrl
	 * but leaves the modifier flags of the next key event empty, which real keyboards always set.
	 */
	private static void ctrl(ClientGameTestContext context, String key) {
		shortcut(context, key, false);
	}

	private static void ctrlShift(ClientGameTestContext context, String key) {
		shortcut(context, key, true);
	}

	private static void shortcut(ClientGameTestContext context, String key, boolean shift) {
		int modifiers = modifierBit(KeyEvent::hasControlDownWithQuirk) | (shift ? modifierBit(KeyEvent::hasShiftDown) : 0);
		int code = InputConstants.getKey("key.keyboard." + key).getValue();
		KeyEvent event = new KeyEvent(code, key.charAt(0), modifiers);
		boolean handled = context.computeOnClient(client -> ScreenHooks.onKey(Screens.current(client), event));
		check(handled, "The mod handled " + (shift ? "Ctrl+Shift+" : "Ctrl+") + key);
		context.waitTicks(2);
	}

	/** The modifier flag for a key, found by asking KeyEvent (GLFW and SDL use different bits). */
	private static int modifierBit(Predicate<KeyEvent> test) {
		for (int bit = 0; bit < 16; bit++) {
			if (test.test(new KeyEvent(0, 0, 1 << bit))) {
				return 1 << bit;
			}
		}
		throw new AssertionError("No modifier bit found");
	}

	private static net.minecraft.client.gui.components.EditBox chatInput(net.minecraft.client.gui.screens.Screen screen) {
		return ((ChatScreenAccessor) screen).bettertyping$input();
	}

	private static String chatText(ClientGameTestContext context) {
		return context.computeOnClient(client -> chatInput(Screens.current(client)).getValue());
	}

	private static String signLine(ClientGameTestContext context, int line) {
		return context.computeOnClient(client -> ((SignEditScreenAccessor) Screens.current(client)).bettertyping$messages()[line]);
	}

	private static net.minecraft.client.gui.components.MultiLineEditBox bookPage(net.minecraft.client.gui.screens.Screen screen) {
		return ((BookEditScreenAccessor) screen).bettertyping$page();
	}

	private static String bookText(ClientGameTestContext context) {
		return context.computeOnClient(client -> bookPage(Screens.current(client)).getValue());
	}

	private static void expect(String expected, String actual, String what) {
		check(Objects.equals(expected, actual), what + ": expected '" + expected + "' but got '" + actual + "'");
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
		BetterTyping.LOGGER.info("[test] OK: {}", message);
	}
}
