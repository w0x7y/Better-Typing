package com.bettertyping.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Player settings, stored as a properties file. Unknown or broken values fall back to defaults. */
public final class Settings {
	public boolean spellCheck = true;
	public boolean autocomplete = true;
	public boolean undoRedo = true;
	public KeyCombo undoKey = KeyCombo.parse("ctrl+z");
	public KeyCombo redoKey = KeyCombo.parse("ctrl+shift+z");
	public KeyCombo redoAltKey = KeyCombo.parse("ctrl+y");
	public List<String> languages = new ArrayList<>(List.of("en_us"));

	public static Settings load(Path file) throws IOException {
		Settings settings = new Settings();
		if (!Files.exists(file)) {
			settings.save(file);
			return settings;
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		settings.spellCheck = bool(props, "spell_check", settings.spellCheck);
		settings.autocomplete = bool(props, "autocomplete", settings.autocomplete);
		settings.undoRedo = bool(props, "undo_redo", settings.undoRedo);
		settings.undoKey = combo(props, "key.undo", settings.undoKey);
		settings.redoKey = combo(props, "key.redo", settings.redoKey);
		settings.redoAltKey = combo(props, "key.redo_alt", settings.redoAltKey);
		String languages = props.getProperty("languages");
		if (languages != null && !languages.isBlank()) {
			settings.languages = new ArrayList<>(List.of(languages.strip().split("\\s*,\\s*")));
		}
		return settings;
	}

	public void save(Path file) throws IOException {
		Properties props = new Properties();
		props.setProperty("spell_check", Boolean.toString(spellCheck));
		props.setProperty("autocomplete", Boolean.toString(autocomplete));
		props.setProperty("undo_redo", Boolean.toString(undoRedo));
		props.setProperty("key.undo", undoKey.toString());
		props.setProperty("key.redo", redoKey.toString());
		props.setProperty("key.redo_alt", redoAltKey.toString());
		props.setProperty("languages", String.join(",", languages));
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			props.store(writer, "Better Typing settings");
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		return switch (value.strip().toLowerCase(Locale.ROOT)) {
			case "true" -> true;
			case "false" -> false;
			default -> fallback;
		};
	}

	private static KeyCombo combo(Properties props, String key, KeyCombo fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return KeyCombo.parse(value);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
