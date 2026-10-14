# Better Typing

Spell check, autocomplete and Ctrl+Z for Minecraft. A client-side Fabric mod that works in chat,
anvil renaming, signs and books, and draws its suggestions the way vanilla draws command suggestions.

![Spelling suggestions in chat](gallery:Spelling suggestions)

Builds are available for Minecraft 1.21.11, 26.1, 26.2 and 26.3. Use the jar for your game version,
with Fabric Loader 0.19.5 or newer and Fabric API. 1.21.11 needs Java 21, 26.x needs Java 25.

## Spell check

Misspelled words get a red squiggle. The word you're still typing isn't underlined until you move past it.
Put the cursor on one and press **Tab**: 3 suggestions show up above it, plus "Add to dictionary".

## Autocomplete

![Autocomplete in chat](gallery:Autocomplete)

After 2 letters, up to 3 words for the one you're typing show up above the field. Accepting one adds a
space after it, like a phone keyboard.

## Picking a suggestion

- **Tab** highlights the first one; every next Tab moves down and wraps around.
- **Space** takes the highlighted one. With nothing highlighted it's just a space.
- **Escape** closes the list first, like vanilla's command list. A second Escape closes the screen.

Lines starting with `/` are left alone, so Tab still completes commands and player names.

## Undo and redo

**Ctrl+Z** undoes, **Ctrl+Shift+Z** and **Ctrl+Y** redo, in every text field. Typing is undone a word at a
time, and an accepted suggestion is its own step. Every sign line and book page keeps its own history.
On macOS, Ctrl is Cmd, like vanilla's copy and paste.

## Words

English, with American spelling. Item and mob names count as correct in the game's language, modded
ones too. Player names don't.

Words you add go to `config/bettertyping/personal_dictionary.txt`. The shortcuts and the on/off switches
are in `config/bettertyping/settings.properties`, for example `key.undo=ctrl+z`.

## Coming later

- A settings screen, with a Mod Menu button and a `/bettertyping` command
- Rebinding the shortcuts in game
- More languages, and British spelling, as downloads

## Credits

The English word list is built from the [English Speller Database](https://github.com/en-wl/wordlist)
(SCOWL) by Kevin Atkinson and sorted with [gwordlist](https://github.com/hackerb9/gwordlist) by hackerb9,
compiled from the Google Books Ngram dataset ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)).

<details>
<summary>Word list notice</summary>

Copyright 2000-2026 by Kevin Atkinson. Permission to use, copy, modify, distribute, and sell any part of
SCOWLv2, or word lists created from it, is hereby granted without fee, provided that the above copyright
notice appears in all copies and that both the above copyright notice and this notice appear in supporting
documentation. Kevin Atkinson makes no representations about the suitability of this database for any
purpose. It is provided "as is" without express or implied warranty.

</details>
