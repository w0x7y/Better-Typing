package com.bettertyping.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
	@TempDir
	Path tmp;

	@Test
	void savedSettingsLoadBack() throws IOException {
		Path file = tmp.resolve("settings.properties");
		Settings settings = new Settings();
		settings.autocomplete = false;
		settings.undoKey = KeyCombo.parse("alt+u");
		settings.languages = List.of("en_gb", "en_us");
		settings.save(file);

		Settings loaded = Settings.load(file);
		assertFalse(loaded.autocomplete);
		assertTrue(loaded.spellCheck);
		assertEquals(KeyCombo.parse("alt+u"), loaded.undoKey);
		assertEquals(List.of("en_gb", "en_us"), loaded.languages);
	}

	@Test
	void missingFileIsCreatedWithDefaults() throws IOException {
		Path file = tmp.resolve("config/settings.properties");
		Settings loaded = Settings.load(file);
		assertTrue(Files.exists(file));
		assertTrue(loaded.autocomplete);
		assertEquals(List.of(DictionarySelection.GAME_LANGUAGE), loaded.languages);
	}

	@Test
	void brokenValuesFallBackToDefaults() throws IOException {
		Path file = tmp.resolve("settings.properties");
		Files.writeString(file, "spell_check=yes\nautocomplete=FALSE\nkey.undo=ctrl+\nlanguages= \n");
		Settings loaded = Settings.load(file);
		assertTrue(loaded.spellCheck, "\"yes\" isn't a boolean");
		assertFalse(loaded.autocomplete);
		assertEquals(new Settings().undoKey, loaded.undoKey);
		assertEquals(List.of(DictionarySelection.GAME_LANGUAGE), loaded.languages);
	}

	@Test
	void keyCombosParseAndMatch() {
		KeyCombo redo = KeyCombo.parse("Ctrl+Shift+Z");
		assertEquals("ctrl+shift+z", redo.toString());
		assertEquals(new KeyCombo("Z", true, true, false), redo);
		assertFalse(redo.equals(KeyCombo.parse("ctrl+z")));
	}

	@Test
	void everyShortcutKeyIsSavedAndLoaded() throws IOException {
		Path file = tmp.resolve("settings.properties");
		for (String key : KeyCombo.SHORTCUT_KEYS) {
			Settings settings = new Settings();
			settings.redoAltKey = new KeyCombo(key, true, false, true);
			settings.save(file);
			assertEquals(settings.redoAltKey, Settings.load(file).redoAltKey, key);
		}
	}

	@Test
	void shortcutsCanNotTakeKeysTheModOrGameUse() {
		for (String key : List.of(KeyCombo.TAB, KeyCombo.ESCAPE, "space", "enter")) {
			assertFalse(KeyCombo.SHORTCUT_KEYS.contains(key), key);
		}
	}
}
