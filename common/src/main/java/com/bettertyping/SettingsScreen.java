package com.bettertyping;

import com.bettertyping.core.KeyCombo;
import com.bettertyping.core.Settings;
import com.bettertyping.core.SpellChecker;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DropdownStringControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen, built with YetAnotherConfigLib. Opened from Mod Menu or with /bettertyping. */
public final class SettingsScreen {
	private static final String KEY = "bettertyping.settings.";
	/** Keys a shortcut can use. Tab, Space, Enter and Escape are left out: the mod and the game already use them. */
	private static final List<String> SHORTCUT_KEYS = shortcutKeys();

	private SettingsScreen() {
	}

	public static Screen create(Screen parent) {
		Settings settings = BetterTyping.settings();
		Settings defaults = new Settings();
		return YetAnotherConfigLib.createBuilder()
			.title(Component.translatable("bettertyping.name"))
			.category(ConfigCategory.createBuilder()
				.name(text("typing"))
				.option(toggle("spell_check", defaults.spellCheck, () -> settings.spellCheck, v -> settings.spellCheck = v))
				.option(toggle("autocomplete", defaults.autocomplete, () -> settings.autocomplete, v -> settings.autocomplete = v))
				.option(toggle("undo_redo", defaults.undoRedo, () -> settings.undoRedo, v -> settings.undoRedo = v))
				.group(shortcut("undo", defaults.undoKey, () -> settings.undoKey, v -> settings.undoKey = v))
				.group(shortcut("redo", defaults.redoKey, () -> settings.redoKey, v -> settings.redoKey = v))
				.group(shortcut("redo_alt", defaults.redoAltKey, () -> settings.redoAltKey, v -> settings.redoAltKey = v))
				.build())
			.category(ConfigCategory.createBuilder()
				.name(text("dictionary"))
				.option(LabelOption.create(text("language")))
				.group(personalDictionary(BetterTyping.checker()))
				.build())
			.save(BetterTyping::saveSettings)
			.build()
			.generateScreen(parent);
	}

	private static Option<Boolean> toggle(String name, boolean fallback, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
			.name(text(name))
			.description(OptionDescription.of(text(name + ".description")))
			.binding(fallback, get, set)
			.controller(TickBoxControllerBuilder::create)
			.build();
	}

	/** One shortcut as Ctrl, Shift and Alt tick boxes plus a key, so it can't be set to something invalid. */
	private static OptionGroup shortcut(String name, KeyCombo fallback, Supplier<KeyCombo> get, Consumer<KeyCombo> set) {
		List<String> keys = new ArrayList<>(SHORTCUT_KEYS);
		if (!keys.contains(get.get().key())) {
			// Set by hand in settings.properties; keep it selectable.
			keys.add(get.get().key());
		}
		Consumer<UnaryOperator<KeyCombo>> edit = change -> set.accept(change.apply(get.get()));
		return OptionGroup.createBuilder()
			.name(text("shortcut." + name))
			.description(OptionDescription.of(text("shortcut." + name + ".description"), text("shortcut.description")))
			.collapsed(true)
			.option(modifier("ctrl", fallback.ctrl(), () -> get.get().ctrl(), v -> edit.accept(c -> new KeyCombo(c.key(), v, c.shift(), c.alt()))))
			.option(modifier("shift", fallback.shift(), () -> get.get().shift(), v -> edit.accept(c -> new KeyCombo(c.key(), c.ctrl(), v, c.alt()))))
			.option(modifier("alt", fallback.alt(), () -> get.get().alt(), v -> edit.accept(c -> new KeyCombo(c.key(), c.ctrl(), c.shift(), v))))
			.option(Option.<String>createBuilder()
				.name(text("shortcut.key"))
				.description(OptionDescription.of(text("shortcut.key.description")))
				.binding(fallback.key(), () -> get.get().key(), v -> edit.accept(c -> new KeyCombo(v, c.ctrl(), c.shift(), c.alt())))
				.controller(option -> DropdownStringControllerBuilder.create(option).values(keys).allowAnyValue(false).allowEmptyValue(false))
				.build())
			.build();
	}

	private static Option<Boolean> modifier(String name, boolean fallback, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
			.name(text("shortcut." + name))
			.binding(fallback, get, set)
			.controller(TickBoxControllerBuilder::create)
			.build();
	}

	/** The personal dictionary as an editable list. Saving adds and removes words to match it. */
	private static ListOption<String> personalDictionary(SpellChecker checker) {
		return ListOption.<String>createBuilder()
			.name(text("personal_dictionary"))
			.description(OptionDescription.of(text("personal_dictionary.description")))
			.binding(List.of(), checker::personalWords, words -> savePersonalWords(checker, words))
			.controller(StringControllerBuilder::create)
			.initial("")
			.insertEntriesAtEnd(true)
			.collapsed(false)
			.build();
	}

	private static void savePersonalWords(SpellChecker checker, List<String> entries) {
		Set<String> wanted = new LinkedHashSet<>();
		for (String entry : entries) {
			for (String word : entry.strip().split("\\s+")) {
				if (!word.isEmpty()) {
					wanted.add(word.toLowerCase(Locale.ROOT));
				}
			}
		}
		try {
			for (String word : checker.personalWords()) {
				if (!wanted.contains(word)) {
					checker.removePersonalWord(word);
				}
			}
			for (String word : wanted) {
				checker.addPersonalWord(word);
			}
		} catch (IOException e) {
			BetterTyping.LOGGER.error("Couldn't save the personal dictionary", e);
		}
	}

	private static Component text(String name) {
		return Component.translatable(KEY + name);
	}

	private static List<String> shortcutKeys() {
		List<String> keys = new ArrayList<>();
		for (char c = 'a'; c <= 'z'; c++) {
			keys.add(String.valueOf(c));
		}
		for (char c = '0'; c <= '9'; c++) {
			keys.add(String.valueOf(c));
		}
		for (int i = 1; i <= 12; i++) {
			keys.add("f" + i);
		}
		keys.addAll(Arrays.asList("backspace", "delete", "insert", "home", "end", "page.up", "page.down", "left", "right", "up", "down"));
		return List.copyOf(keys);
	}
}
