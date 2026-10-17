package com.bettertyping;

import com.bettertyping.core.DictionarySelection;
import com.bettertyping.core.KeyCombo;
import com.bettertyping.core.LanguagePacks;
import com.bettertyping.core.Settings;
import com.bettertyping.core.SpellChecker;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DropdownStringControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen, built with YetAnotherConfigLib. Opened from Mod Menu or with /bettertyping. */
public final class SettingsScreen {
	private static final String KEY = "bettertyping.settings.";

	private SettingsScreen() {
	}

	public static Screen create(Screen parent) {
		// A download that failed is tried again whenever the settings open.
		Languages.choose();
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
				.group(languages(settings))
				.group(personalDictionary(BetterTyping.checker()))
				.build())
			.save(() -> {
				Languages.choose();
				BetterTyping.saveSettings();
			})
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
		List<String> keys = new ArrayList<>(KeyCombo.SHORTCUT_KEYS);
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

	/**
	 * Which languages' words to use, one tick box each: the game's language and the catalog's. Saving with
	 * a language that isn't installed downloads it.
	 */
	private static OptionGroup languages(Settings settings) {
		List<String> choices = new ArrayList<>();
		choices.add(DictionarySelection.GAME_LANGUAGE);
		for (LanguagePacks.Language language : Languages.packs().languages()) {
			choices.add(language.code());
		}
		for (String code : settings.languages) {
			if (!choices.contains(code)) {
				// Set by hand in settings.properties; keep it selectable.
				choices.add(code);
			}
		}
		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(text("languages"))
			.description(OptionDescription.of(text("languages.description")));
		for (String code : choices) {
			group.option(Option.<Boolean>createBuilder()
				.name(Languages.name(code))
				.description(OptionDescription.of(text("languages.description")))
				.binding(code.equals(DictionarySelection.GAME_LANGUAGE), () -> settings.languages.contains(code), on -> {
					settings.languages.remove(code);
					if (on) {
						settings.languages.add(code);
					}
				})
				// Worked out as it's drawn, so a finished download stops saying "download".
				.controller(option -> BooleanControllerBuilder.create(option).formatValue(on -> Languages.status(code, on)))
				.build());
		}
		return group.build();
	}

	/** The personal dictionary as an editable list. Saving replaces its words with the list. */
	private static ListOption<String> personalDictionary(SpellChecker checker) {
		return ListOption.<String>createBuilder()
			.name(text("personal_dictionary"))
			.description(OptionDescription.of(text("personal_dictionary.description")))
			.binding(List.of(), checker::personalWords, checker::setPersonalWords)
			.controller(StringControllerBuilder::create)
			.initial("")
			.insertEntriesAtEnd(true)
			.collapsed(false)
			.build();
	}

	private static Component text(String name) {
		return Component.translatable(KEY + name);
	}
}
