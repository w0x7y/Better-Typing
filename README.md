# Better Typing

Client-side Fabric mod: spell check, autocomplete and undo/redo in every text field.

Supports Minecraft **1.21.11, 26.1.x, 26.2 and 26.3**, one jar per version.

```sh
./gradlew build                 # all versions: versions/<mc>/build/libs/
./gradlew :26.3:build           # one version
./gradlew :26.3:runClient       # dev client (worlds and options kept in run/26.3)
./gradlew :core:test            # unit tests for the logic
```

Gradle runs on Java 25 (set in `gradle/gradle-daemon-jvm.properties`). 1.21.11 jars target Java 21.

## Project layout

| Folder | What's in it |
|---|---|
| `core/` | Pure-Java logic with unit tests: dictionary lookups, suggestions, autocomplete, undo history, settings. No Minecraft code. |
| `common/` | Minecraft code shared by every version: screen hooks, drawing the underline and popup, game words, entrypoint. |
| `variants/1.21.11`, `variants/26.x` | Rendering hooks: `GuiGraphics` (1.21.11) vs `GuiGraphicsExtractor` (26.x). 1.21.11 also needs a mixin for typed characters. |
| `variants/keys-glfw`, `variants/keys-sdl` | Shortcut key codes: GLFW up to 26.2, SDL from 26.3. |
| `versions/<mc>/` | One Gradle project per Minecraft version. `gradle.properties` picks the versions of Minecraft, Fabric API and Mod Menu, plus which variants to use. |
| `tools/build_dictionary.py` | Rebuilds the bundled word list (see below). |

## Status

| Feature | State |
|---|---|
| Spell check underline + Tab suggestions | Done for chat and anvil rename |
| Autocomplete | Done for chat and anvil rename |
| Undo / redo | Done for every single-line text field |
| Item and mob names count as correct | Done (current game language, includes modded) |
| Personal dictionary | Done (`config/bettertyping/personal_dictionary.txt`) |
| Signs, books | Not started |
| Settings screen, Mod Menu button, `/bettertyping` command, rebinding UI | Not started. Shortcuts can be changed in `config/bettertyping/settings.properties` meanwhile. |
| Language downloads | Not started |

## Behaviour

Spell check and autocomplete: chat (not commands), anvil rename, signs, books.
Undo/redo: every text field.

### Spell check
- Misspelled words get a red squiggly underline. The word you're still typing isn't underlined until you move past it.
- Move the cursor onto a misspelled word and press **Tab**: a popup above the field shows 3 suggestions.

### Autocomplete
- While typing, up to 3 completions for the current word appear above the field (from 2 letters on).
- Accepting a completion also adds a space after it, like phone keyboards.

### Choosing a suggestion
Same controls for spell-check suggestions and autocomplete:
- **Tab** highlights the first suggestion; each further Tab moves to the next one, wrapping around.
- **Space** confirms the highlighted suggestion and replaces the word.
- If nothing is highlighted yet, Space types a normal space.
- **Escape** closes the popup first, the way vanilla's command suggestions do; a second Escape closes the screen.

### Tab priority
- **Commands:** when the line starts with `/`, Tab keeps its vanilla behaviour (command and player-name completion). Spell check and autocomplete are off for commands.
- **Misspelling vs autocomplete:** while you're typing at the end of a word, autocomplete gets Tab. Once the cursor sits on a finished word, spelling suggestions do.

### Dictionary
- English only by default.
- A settings menu lists languages, with a way to download more later. The download source is not decided yet.
- Item names and mob names always count as correct. Player names do not.
- **Personal dictionary:** the spelling popup has an "Add to dictionary" entry after the 3 suggestions. Added words are saved locally and can be removed in the settings menu.

### Undo / redo
- **Ctrl+Z** undoes. **Ctrl+Shift+Z** and **Ctrl+Y** both redo, in every text field. On macOS, Ctrl means Cmd, like vanilla's copy/paste.
- Typing is undone a word at a time; an accepted suggestion is its own undo step.
- All three shortcuts can be rebound in the mod's settings menu.
- Vanilla's Controls screen only binds single keys, so these combos need the mod's own rebinding UI.

### Settings menu
- Opened from Mod Menu (optional dependency) or with the `/bettertyping` client command.

## Word list

The bundled English dictionary (`common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt`) has 109,902 words, one per line, most common first.

- **Which words are correct:** [English Speller Database (ESDB)](https://github.com/en-wl/wordlist), previously SCOWL, release `rel-2026.02.25`, size 60, American spelling. Same source as the LibreOffice/Hunspell English dictionaries.
- **Order (for autocomplete):** word counts from Google Books via [hackerb9/gwordlist](https://github.com/hackerb9/gwordlist) (`frequency-alpha-alldicts.txt`). Words without a count follow, ordered by ESDB size.

### Licenses
- **ESDB:** MIT-style. Commercial use, modification and redistribution are allowed. Keep Kevin Atkinson's copyright notice in the mod jar and the docs.
- **gwordlist / Google Books Ngram counts:** Creative Commons Attribution 3.0. Commercial use allowed; credit is required.
- Stay at size 70 or smaller: lists larger than 80 pull in the UK Advanced Cryptics Dictionary, which requires including its notice word for word. Don't use Australian (`AU`) data without adding its separate notice.
- British spellings ("colour") are flagged by default and will come as a separate download from the language menu.

### Rebuilding

```sh
git clone --depth 1 --branch rel-2026.02.25 https://github.com/en-wl/wordlist.git esdb
(cd esdb && make && for s in 35 40 50 60; do ./scowl word-list $s A 1 --deaccent > ../wl-$s.txt; done)
curl -LO https://raw.githubusercontent.com/hackerb9/gwordlist/master/frequency-alpha-alldicts.txt
python3 tools/build_dictionary.py --esdb-lists wl-35.txt wl-40.txt wl-50.txt wl-60.txt \
  --frequency frequency-alpha-alldicts.txt --max-size 60 \
  --out common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt
```
