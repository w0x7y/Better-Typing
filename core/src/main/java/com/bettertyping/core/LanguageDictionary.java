package com.bettertyping.core;

/**
 * The language dictionary: one language's words with its chat words moved up (see {@link WordList#language}),
 * as {@link LanguagePacks#load} puts it together.
 *
 * @param language its code, e.g. {@code en_us}
 */
public record LanguageDictionary(String language, WordList words) {
}
