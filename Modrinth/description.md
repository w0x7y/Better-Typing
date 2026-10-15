# Better Typing

Spell check, autocomplete and Ctrl+Z for Minecraft. A client-side Fabric mod that works in chat,
anvil renaming, signs and books, and draws its suggestions the way vanilla draws command suggestions.

![Spelling suggestions in chat](gallery:Spelling suggestions)

Builds are available for Minecraft 1.21.11, 26.1, 26.2 and 26.3. Use the jar for your game version,
with Fabric Loader 0.19.5 or newer, Fabric API and [YetAnotherConfigLib](https://modrinth.com/mod/yacl).
1.21.11 needs Java 21, 26.x needs Java 25.

## Spell check

Misspelled words get a red squiggle. The word you're still typing isn't underlined until you move past it.
Put the cursor on one and press **Tab**: 3 suggestions show up above it, plus "Add to dictionary".

## Autocomplete

![Autocomplete in chat](gallery:Autocomplete)

After 2 letters, up to 3 words for the one you're typing show up above the field. Accepting one adds a
space after it, like a phone keyboard.

Words people say in chat come first, so `hel` gives hello, help and held, and `cr` gives craft before cross.

## Picking a suggestion

- **Tab** highlights the first one; every next Tab moves down and wraps around.
- **Space** takes the highlighted one. With nothing highlighted it's just a space.
- **Escape** closes the list first, like vanilla's command list. A second Escape closes the screen.

Lines starting with `/` are left alone, so Tab still completes commands and player names.

On a sign, when the list would cover a line you wrote, it goes below the word or beside the text instead.

## Undo and redo

**Ctrl+Z** undoes, **Ctrl+Shift+Z** and **Ctrl+Y** redo, in every text field. Typing is undone a word at a
time, and an accepted suggestion is its own step. Every sign line and book page keeps its own history.
On macOS, Ctrl is Cmd, like vanilla's copy and paste.

## Languages

English with American or British spelling comes with the mod. German, Spanish, French, Italian, Dutch,
Brazilian Portuguese and Swedish are a download each, about 1 MB.

Pick one in the settings and press Save. Only then does the mod connect to the internet: it downloads the
language from the mod's [GitHub releases](https://github.com/w0x7y/Better-Typing/releases/tag/languages-1.0.0)
and keeps it only if its SHA-512 matches the one written into the mod. You can also put a language's jar
in the mods folder like any mod, for a modpack or to play offline.

The default, Game language, uses the game's language when it's installed, and American English otherwise.

Item and mob names count as correct in the game's language, modded ones too, and so do chat words like
gg, afk and lol, in every language. Player names don't.

## Settings

![The settings screen](gallery:Settings)

Open them with `/bettertyping`, or the config button in Mod Menu.

- Turn spell check, autocomplete and undo/redo on or off.
- Change the three shortcuts: tick Ctrl, Shift and Alt and pick a key.
- Pick the language.
- See and remove the words in your personal dictionary.

## Coming later

- Russian, Polish, Turkish, Czech and Ukrainian. They have millions of word forms, so they need a more
  compact word list first.

## Credits

The English word lists are built from the [English Speller Database](https://github.com/en-wl/wordlist)
(SCOWL) by Kevin Atkinson and sorted with [gwordlist](https://github.com/hackerb9/gwordlist) by hackerb9,
compiled from the Google Books Ngram dataset ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)).

The other languages are separate downloads under the GNU GPL version 3, built from the
[LibreOffice dictionaries](https://github.com/LibreOffice/dictionaries) and ordered with
[wordfreq](https://github.com/rspeer/wordfreq) by Robyn Speer. Each one's page and NOTICE.txt credit its
sources.

<details>
<summary>Word list notice</summary>

Copyright 2000-2026 by Kevin Atkinson. Permission to use, copy, modify, distribute, and sell any part of
SCOWLv2, or word lists created from it, is hereby granted without fee, provided that the above copyright
notice appears in all copies and that both the above copyright notice and this notice appear in supporting
documentation. Kevin Atkinson makes no representations about the suitability of this database for any
purpose. It is provided "as is" without express or implied warranty.

</details>
