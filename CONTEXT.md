# Better Typing: domain terms

**Field**: one editable piece of text on screen: a text box (chat, anvil rename, book title), one line of a sign, or one page of a book. Each field has a key that stays the same while it exists; a book reuses one text box for every page, so its pages are separate fields. Chat, anvil and book title screens build a new text box whenever they resize, so those fields keep a key of their own across it.

**Assist**: whether a field gets spell check and autocomplete. Chat lines starting with `/` (commands) and text boxes other than chat, anvil rename and book titles don't. Undo/redo works in every field. Decided only in `ScreenFields`.

**Typing session**: everything the mod does for one open screen: each field's undo history and suggestion list, which field was typed in last, and how keys are handled. Lives exactly as long as its screen (`TypingSession`).

**Word sources**: what counts as a correct word: the language dictionaries (every one of the player's languages that's installed, each a `LanguageDictionary`; "game language" follows the game's own, and English (US) stands in when none is installed), game words (item and mob names in the game's language) and the personal dictionary (words the player added). Replaced as one snapshot whenever any of them changes.

**Chat words**: a language's list of words common in Minecraft chat (`en_us.chat.txt`), most common first. Part of the language dictionary: they rank higher than their book frequency, and the ones the dictionary lacks ("gg", "afk") count as correct. Every language has its own (`de_de.chat.txt`), followed by the gaming slang they all share (`chat.txt`).

**Language pack**: a language that isn't bundled with the mod: a small jar holding one word list, downloaded when the player picks that language (`LanguagePacks`). The mod's catalog (`languages.txt`) pins each pack's size, SHA-512 and URL on the GitHub release, so it keeps only that exact file. Packs installed as mods work too. Elisions in a list (`l'`, `dell'`) aren't words but let a word follow them.

**Dictionary selection**: keeping the language dictionaries in step with the player's languages and the game's (`DictionarySelection`): it loads every installed one, downloads a chosen pack that's missing or whose pinned file changed, and drops a load a newer choice overtook. "Game language" never downloads, and nothing chosen means game language. English (US) stands in when none of the chosen languages is installed or readable. A failed download is tried again when the settings open or the game restarts.

**Compound**: a word made of known words joined together (German "Diamantschwert"), accepted in languages whose rules file (`de_de.rules.txt`) says how parts join: linking letters, endings a part can drop, and how many of the most common words can't be parts. The last part decides the capitalization.

**Misspelling**: a word not found in any word source. Underlined with a red squiggle, except the word being typed at the cursor, until it's finished.

**Suggestion list** (popup): the list next to a word. It goes above the word like vanilla's command suggestions; where that would cover another line of a sign it goes below the word or beside the text instead. *Autocomplete* lists appear while typing, with nothing highlighted; *spelling* lists open with Tab on a misspelling, first row highlighted, plus "Add to dictionary". Tab moves the highlight, Space accepts it, Escape closes the list.

**Undo step**: what one Ctrl+Z reverts: a typed word with the spaces after it, a run of deletions, a paste, or one accepted suggestion.

**Overlay**: what a typing session reports to draw for a screen: its misspellings and the focused field's suggestion list. Where each goes on screen is worked out in one place (`OverlayLayout`).

**Variant**: code for a group of Minecraft versions that share an API (`variants/`), e.g. how the screen is drawn or which key codes are used. Each version picks its variants in `versions/<mc>/gradle.properties`.
