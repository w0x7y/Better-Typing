package com.bettertyping.test;

import com.bettertyping.BetterTyping;
import com.bettertyping.ScreenHooks;
import com.bettertyping.core.DictionarySelection;
import com.bettertyping.core.KeyCombo;
import com.bettertyping.core.TextState;
import com.bettertyping.core.TypingField;
import com.bettertyping.field.ScreenFields;
import com.bettertyping.mixin.AnvilScreenAccessor;
import com.bettertyping.mixin.BookEditScreenAccessor;
import com.bettertyping.mixin.ChatScreenAccessor;
import com.bettertyping.mixin.MultiLineEditBoxAccessor;
import com.bettertyping.mixin.SignEditScreenAccessor;
import com.bettertyping.version.Screens;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Plays through chat, a sign, a book and its title, and an anvil in a fresh world, checking the text
 * after each step and saving screenshots of the underlines and popups, then opens the settings screen.
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
			anvil(context, world.getServer());
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
		String tooLong = "line\n".repeat(20);
		context.runOnClient(client -> focusedField(client).apply(TextState.of(tooLong, 0)));
		check(bookText(context).startsWith("Ths is a tset"), "A book page refuses text past its last line, like typing does");

		// Enter on the focused Sign button opens the signing screen, whose title box is focused.
		context.runOnClient(client -> {
			Screen screen = Screens.current(client);
			for (GuiEventListener child : screen.children()) {
				if (child instanceof Button button && button.getMessage().getString().equals(I18n.get("book.signButton"))) {
					screen.setFocused(button);
				}
			}
		});
		press(context, "enter");
		context.waitForScreen(BookSignScreen.class);
		assistedBox(context, "the book title");
		close(context);
	}

	private static void anvil(ClientGameTestContext context, TestServerContext server) {
		// On the floor in front, looking down at it, with nothing in hand to use instead. Anvils fall, so not on the wall.
		server.runCommand("setblock 0 101 1 air");
		server.runCommand("setblock 0 100 1 anvil");
		server.runCommand("tp @a 0.5 100 0.5 0 30");
		server.runCommand("item replace entity @a weapon.mainhand with air");
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitForScreen(AnvilScreen.class);
		// The name box only takes text with an item to rename, and there's none.
		context.runOnClient(client -> ((AnvilScreenAccessor) Screens.current(client)).bettertyping$name().setEditable(true));
		assistedBox(context, "the anvil's name box");
		close(context);
	}

	/** Checks that the focused box gets spell check and autocomplete, and that a typed word undoes as one step. */
	private static void assistedBox(ClientGameTestContext context, String what) {
		type(context, "teh");
		check(context.computeOnClient(client -> focusedField(client).assist() && focusedField(client).state().text().equals("teh")),
			what + " gets spell check and autocomplete");
		ctrl(context, "z");
		check(context.computeOnClient(client -> focusedField(client).state().text().isEmpty()), "Undo in " + what);
	}

	private static TypingField focusedField(Minecraft client) {
		return ScreenFields.find(Screens.current(client)).focused();
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
			((Option<Boolean>) option(screen, "autocomplete")).requestSet(false);
			screen.finishOrSave();
		});
		check(!BetterTyping.settings().autocomplete, "Saving the screen applies the change");
		check(settingsFile().contains("autocomplete=false"), "Saving the screen writes the settings file");

		context.runOnClient(client -> ((YACLScreen) Screens.current(client)).tabNavigationBar.selectTab(1, false));
		context.waitTicks(2);
		context.takeScreenshot("bettertyping-13-settings-dictionary");
		context.runOnClient(client -> {
			YACLScreen screen = (YACLScreen) Screens.current(client);
			ListOption<String> words = (ListOption<String>) group(screen, "personal_dictionary");
			check(words.pendingValue().contains(TEST_WORD), "The personal dictionary lists added words");
			words.requestSet(List.of());
			screen.finishOrSave();
		});
		check(!BetterTyping.checker().isCorrect(TEST_WORD), "Removing a word from the list removes it from the dictionary");

		// English (UK) is bundled, so ticking it switches the dictionary without a download.
		setLanguages(context, "English (UK)");
		context.waitFor(client -> BetterTyping.checker().isCorrect("colour") && !BetterTyping.checker().isCorrect("color"));
		check(true, "Ticking only English (UK) in the settings loads British spelling");
		setLanguages(context, "Game language", "English (UK)");
		context.waitFor(client -> BetterTyping.checker().isCorrect("colour") && BetterTyping.checker().isCorrect("color"));
		check(true, "Ticking two languages accepts the words of both");
		setLanguages(context);
		context.runOnClient(client -> check(group((YACLScreen) Screens.current(client), "languages").options().stream().noneMatch(Option::changed),
			"Saving with nothing ticked leaves the screen showing what was saved"));
		setLanguages(context, "Game language");
		context.waitFor(client -> BetterTyping.checker().isCorrect("color") && !BetterTyping.checker().isCorrect("colour"));
		check(settingsFile().lines().anyMatch(line -> line.equals("languages=" + DictionarySelection.GAME_LANGUAGE)), "Game language alone goes back to English (US)");

		BetterTyping.settings().autocomplete = true;
		BetterTyping.saveSettings();
		close(context);
	}

	/** Ticks exactly the languages with these names on the settings screen's dictionary tab and presses Save. */
	@SuppressWarnings("unchecked")
	private static void setLanguages(ClientGameTestContext context, String... names) {
		context.runOnClient(client -> {
			YACLScreen screen = (YACLScreen) Screens.current(client);
			for (Option<?> option : group(screen, "languages").options()) {
				String name = option.name().getString();
				((Option<Boolean>) option).requestSet(Arrays.stream(names).anyMatch(name::endsWith));
			}
			screen.finishOrSave();
		});
	}

	/** The settings group with this key ("languages"), wherever the screen puts it. */
	private static OptionGroup group(YACLScreen screen, String key) {
		return screen.config.categories().stream().flatMap(category -> category.groups().stream())
			.filter(group -> isSetting(group.name(), key)).findFirst().orElseThrow();
	}

	/** The settings option with this key ("autocomplete"), wherever the screen puts it. */
	private static Option<?> option(YACLScreen screen, String key) {
		return screen.config.categories().stream().flatMap(category -> category.groups().stream())
			.flatMap(group -> group.options().stream()).filter(option -> isSetting(option.name(), key)).findFirst().orElseThrow();
	}

	private static boolean isSetting(Component name, String key) {
		return name.getContents() instanceof TranslatableContents text && text.getKey().equals("bettertyping.settings." + key);
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
