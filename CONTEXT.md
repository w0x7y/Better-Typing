# Better Typing: domain terms

**Field**: one editable piece of text on screen: a text box (chat, anvil rename, book title), one line of a sign, or one page of a book. Each field has a key that stays the same while it exists; a book reuses one text box for every page, so its pages are separate fields.

**Assist**: whether a field gets spell check and autocomplete. Chat lines starting with `/` (commands) and text boxes other than chat, anvil rename and book titles don't. Undo/redo works in every field. Decided only in `ScreenFields`.

**Typing session**: everything the mod does for one open screen: each field's undo history and suggestion list, which field was typed in last, and how keys are handled. Lives exactly as long as its screen (`TypingSession`).

**Word sources**: what counts as a correct word: the language dictionary, game words (item and mob names in the game's language) and the personal dictionary (words the player added). Replaced as one snapshot whenever any of them changes.

**Misspelling**: a word not found in any word source. Underlined with a red squiggle, except the word being typed at the cursor, until it's finished.

**Suggestion list** (popup): the list above a word. *Autocomplete* lists appear while typing, with nothing highlighted; *spelling* lists open with Tab on a misspelling, first row highlighted, plus "Add to dictionary". Tab moves the highlight, Space accepts it, Escape closes the list.

**Undo step**: what one Ctrl+Z reverts: a typed word with the spaces after it, a run of deletions, a paste, or one accepted suggestion.

**Overlay**: what a typing session reports to draw for a screen: its misspellings and the focused field's suggestion list.

**Variant**: code for a group of Minecraft versions that share an API (`variants/`), e.g. how the screen is drawn or which key codes are used. Each version picks its variants in `versions/<mc>/gradle.properties`.
