**Better Typing**

spell check, autocomplete and ctrl+z for minecraft

<img width="440" alt="spelling suggestions in chat" src="assets/spelling.png" />

a client-side fabric mod for **1.21.11, 26.1, 26.2 and 26.3**. it works in chat, anvil renaming, signs and books, and draws its suggestions the way vanilla draws command suggestions. it needs fabric api and [yetanotherconfiglib](https://modrinth.com/mod/yacl)

**spell check**

misspelled words get a red squiggle. the word you're still typing isn't underlined until you move past it

put the cursor on one and press **tab**: 3 suggestions show up above it, plus "add to dictionary"

**autocomplete**

<img width="270" alt="autocomplete in chat" src="assets/autocomplete.png" />

after 2 letters, up to 3 words for the one you're typing show up above the field. accepting one adds a space after it, like a phone keyboard

words people say in chat come first, so `hel` gives hello, help and held, and `cr` gives craft before cross

**picking a suggestion**

- **tab** highlights the first one, every next tab moves down and wraps around
- **space** takes the highlighted one. with nothing highlighted it's just a space
- **escape** closes the list first, like vanilla's command list. a second escape closes the screen

lines starting with `/` are left alone, so tab still completes commands and player names

on a sign, when the list would cover a line you wrote, it goes below the word or beside the text instead

**undo and redo**

**ctrl+z** undoes, **ctrl+shift+z** and **ctrl+y** redo, in every text field. typing is undone a word at a time, an accepted suggestion is its own step. every sign line and book page keeps its own history. on macos ctrl is cmd, like vanilla's copy and paste

**words**

english, with american spelling. item and mob names count as correct in the game's language, modded ones too. so do chat words like gg, afk and lol. player names don't

**settings**

<img width="560" alt="the settings screen" src="assets/settings.png" />

open them with `/bettertyping`, or the config button in mod menu

- turn spell check, autocomplete and undo/redo on or off
- change the three shortcuts: tick ctrl, shift and alt and pick a key
- see and remove the words in your personal dictionary

they're saved to `config/bettertyping/settings.properties` and `config/bettertyping/personal_dictionary.txt`

**not there yet**

- [ ] more languages and british spelling as downloads

**[building](./versions)**

gradle runs on java 25, the 1.21.11 jar targets java 21

```sh
./gradlew build                     # every version, jars in versions/<mc>/build/libs/
./gradlew :26.3:runClient           # a dev client, its worlds stay in run/26.3
./gradlew :core:test                # unit tests
./gradlew :26.3:runClientGameTest   # opens the game, types through chat, a sign and a book, then the settings
```

the in-game test checks the text after every step (25 checks) and saves screenshots to `run/<mc>/screenshots/`. the images above come from there

<details>
<summary>how it's laid out</summary>

<br>

| folder | what's in it |
|---|---|
| `core/` | plain java, no minecraft. `TypingSession` is the way in: one per screen, it takes keys and characters and says what to draw. behind it are the spell checker, autocomplete and undo history |
| `common/` | minecraft code every version shares: screen hooks, drawing, item and mob names, the settings screen. `field/` adapts each kind of text field (text box, sign line, book page) |
| `common/src/gametest/` | the in-game test |
| `variants/` | code for groups of versions that share an api: how screens draw, which key codes there are, small 26.2 changes |
| `versions/<mc>/` | one gradle project per version. its `gradle.properties` picks minecraft, fabric api, yacl, mod menu and which variants to use |

the words used in the code are in [CONTEXT.md](CONTEXT.md)

</details>

**[the word list](./tools/build_dictionary.py)**

109,902 english words in `common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt`, one per line, most common first

- which words are correct: the [english speller database](https://github.com/en-wl/wordlist) (scowl), release `rel-2026.02.25`, size 60, american. the same source as the libreoffice and hunspell english dictionaries
- the order: google books counts from [gwordlist](https://github.com/hackerb9/gwordlist). words without a count go last
- chat words: books rarely say hello or gg, so `en_us.chat.txt` next to it lists words common in minecraft chat, most common first. the n-th one ranks at least as high as the dictionary's (n × 3)-th word, and the ones the dictionary lacks count as correct

<details>
<summary>rebuilding it</summary>

<br>

```sh
git clone --depth 1 --branch rel-2026.02.25 https://github.com/en-wl/wordlist.git esdb
(cd esdb && make && for s in 35 40 50 60; do ./scowl word-list $s A 1 --deaccent > ../wl-$s.txt; done)
curl -LO https://raw.githubusercontent.com/hackerb9/gwordlist/master/frequency-alpha-alldicts.txt
python3 tools/build_dictionary.py --esdb-lists wl-35.txt wl-40.txt wl-50.txt wl-60.txt \
  --frequency frequency-alpha-alldicts.txt --max-size 60 \
  --out common/src/main/resources/assets/bettertyping/dictionaries/en_us.txt
```

stay at size 70 or below. past 80 the list pulls in the uk advanced cryptics dictionary, which needs its own notice, and the australian data needs one too

</details>

<details>
<summary>word list notices</summary>

<br>

Copyright 2000-2026 by Kevin Atkinson. Permission to use, copy, modify, distribute, and sell any part of SCOWLv2, or word lists created from it, is hereby granted without fee, provided that the above copyright notice appears in all copies and that both the above copyright notice and this notice appear in supporting documentation. Kevin Atkinson makes no representations about the suitability of this database for any purpose. It is provided "as is" without express or implied warranty.

gwordlist by hackerb9 is compiled from the Google Books Ngram dataset by Google, licensed under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). it's only used to sort the words.

both ship in every jar as [THIRD_PARTY_NOTICES.txt](common/src/main/resources/THIRD_PARTY_NOTICES.txt). keep that file, and these notices on the download page

</details>

_credits: [english speller database](https://github.com/en-wl/wordlist) by kevin atkinson, [gwordlist](https://github.com/hackerb9/gwordlist) by hackerb9. license: [mit](./LICENSE)_
