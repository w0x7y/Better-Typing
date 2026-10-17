# Language downloads: research

Researched 2026-10-14 for the roadmap item "more languages and british spelling as downloads". Built in 0.2.0; see [What was built](#what-was-built) for where it differs from the plan.

## What was built

- **British English is bundled**, built like American English with ESDB's British lists. Its chat words only change "armor" to "armour".
- **Seven language packs**: German, Spanish (Spain and Mexico dictionaries together), French, Italian, Dutch, Brazilian Portuguese and Swedish. `tools/build_language_packs.py` walks wordfreq's list for the language, most common first, and keeps each word the LibreOffice dictionary accepts (checked with spylls, a Python port of Hunspell), up to 200k. Checking wordfreq's words, rather than expanding the dictionary, also keeps German, Dutch and Swedish compounds that people actually write.
- **GitHub only, pinned**: the packs are assets of the GitHub release `languages-1.0.0`, not Modrinth projects. Seven extra projects next to the mod would be confusing and clutter Modrinth. The mod's catalog (`languages.txt`) pins each pack's URL, size and SHA-512 and keeps only that file, so a new pack needs a mod update and nobody can swap the file. There's no API call.
- **Picked in the settings**: the language option lists every language; one that isn't installed shows its download size and is downloaded when you press Save. Since 0.4.0 each language is a tick box with its flag, and every ticked one is used at once. The default, "Game language", uses the game's language only if it's installed, and never downloads.
- **Elisions**: French and Italian lists end with the elisions their dictionary allows (`l'`, `dell'`), which let a word follow them. Our tokenizer keeps "l'homme" as one word, and wordfreq splits it, so without them every elided word would be flagged.
- **Chat words for every language**: each language has its own chat list (`de_de.chat.txt`: hallo, digga, kk), checked against wordfreq so every word is really used, and they all get the gaming slang and Minecraft words in `chat.txt` (gg, afk, pvp, xD).
- **Compounds without a bigger list** (0.3.0): German, Dutch and Swedish accept a word made of known words, with the linking letters and droppable endings in `<code>.rules.txt`. The 50 most common words can't be parts, so "ichbin" stays wrong, and the last part sets the capitalization, so "spielplatz" stays wrong in German.
- **Packs 1.1.0** (0.3.0): Hunspell accepts any word in capitals, so 1.0.0 lists held forms like WEISS that autocomplete offered; capitals are now kept only when the dictionary lists the word so (ZDF), and Dutch tries the IJ capital (IJsland). wordfreq writes ß as ss, which the German dictionary rejects, so 1.0.0 lacked weiß and Straße; German words are now also checked with ß. The builder writes the catalog lines itself. A player who chose a language gets the new pack automatically, because a pinned file that changed counts as not installed and a chosen language is downloaded when it's missing.
- **Russian, Hebrew, Arabic and Portuguese (Portugal)** (0.3.0): because the lists are wordfreq's words that the dictionary accepts, rather than every form the dictionary allows, these fit the same 200k flat list as the others. Rarer forms get flagged. Hebrew's words come from Hspell, which is AGPL-3.0, so its pack is AGPL-3.0 combined with wordfreq's GPL-3.0 order. Arabic chat often leaves out the hamza (انا for أنا), so the builder keeps those spellings when the dictionary accepts the word with it, and the mod ignores Arabic and Hebrew vowel marks.

## Recommendation

- **Host on Modrinth.** Publish each language as its own small Modrinth project: a Fabric mod jar that holds only the word list and its licence. The settings screen gets a "Download" button that fetches the newest file from `cdn.modrinth.com` through the Modrinth API and checks the SHA-512 the API returns. The same jar can be installed from a launcher or put in a modpack, for offline use. A GitHub Release asset can be an optional fallback.
- **Words from the LibreOffice dictionaries, frequencies from wordfreq.** Expand the hunspell dictionary into word forms and keep the forms wordfreq has seen, most frequent first, capped at about 200k. British English comes from the same source as American English (ESDB).
- **First batch:** British English, Spanish, Brazilian Portuguese, German, French, Italian, Dutch and Swedish. These fit the current flat list.
- **Later:** Russian, Polish, Turkish, Ukrainian and Czech need about 500k+ forms, so they need a compact word store first. Finnish, Hungarian, Hebrew, Arabic, Korean, Chinese, Japanese and Thai are deferred.
- **Licence:** each language file is a separate download under its own licence (GPL-3.0 for the wordfreq-ranked lists), so the mod stays MIT.

## Where to download from

### Rules

Modrinth's [Content Rules](https://modrinth.com/legal/rules) (last modified 2026-08-13) have no rule against a mod downloading data. The clauses that touch this:

- §1: don't "upload any data to a remote server (i.e. one that the user does not directly choose to connect to in-game) without clear disclosure". This is about uploads; we only download.
- §3: projects "cannot contain or download 'cheats'".
- §5.7: "Additional files" are only for special purposes such as source jars, so dictionaries can't be extra files on the mod's own versions.
- §5.8: "Files with different functionality should not be uploaded as new versions within a single project; instead, they should be published as separate projects." So each language is its own project.

The [Content Disclosures guide](https://support.modrinth.com/en/articles/16567675) says data transfers "expected in a project's functionality" need no disclosure flag. The description should still say that the mod connects to Modrinth when you press Download.

The [Modrinth API](https://docs.modrinth.com/api/) asks for a unique User-Agent and allows 300 requests per minute per IP.

CurseForge only bans external download links on project pages. Its "Project Variants" rule may want the languages merged into one project if we ever publish there.

### Hosts compared

| Host | Limits and terms | China / Russia | Verdict |
|---|---|---|---|
| Modrinth (separate projects) | API licence covers downloads; moderators approve each project | Same reach as the mod itself. Chinese launchers (HMCL, PCL) rewrite `cdn.modrinth.com` to the MCIM mirror, which only mirrors mods under 20 MB | **Primary** |
| GitHub Releases | "No limit on the total size of a release, nor bandwidth usage"; files under 2 GiB; may throttle "significantly excessive" use | Interfered with in 83% of recent tests in China; failures rising in Russia (16% in May 2026) | Fallback |
| raw.githubusercontent.com | Rate-limited without auth | Blocked in China, blamed in Russia | No |
| jsDelivr | Free, 20 MB per GitHub file; terms forbid "general-purpose file hosting" | Intermittent in China (lost its ICP licence in 2021) | No |
| Cloudflare R2 / Pages | Free egress, needs our own domain | Russian ISPs cut Cloudflare connections after 16 KB since June 2025 | No |
| Maven Central | Sonatype is enforcing consumption limits | — | No |
| Codeberg | No availability guarantees, warns about heavy traffic | — | No |

### How others do it

- **I18nUpdateMod** (17.7M downloads) fetches translation packs at runtime from GitHub and two Chinese mirrors in parallel, takes the first that answers, checks an `.md5` and caches in the app-data folder.
- **Distant Horizons** updates itself from the Modrinth API (it can be turned off) and checks the file's SHA-1.
- **Save My Shaky Network** exists because other mods' web requests stall or crash the game in China. Downloads must run off the main thread with short timeouts.
- **Spell Checker** and **Lexikon** (the competing mods) bundle every language: 10 languages in 5 MB and 8 in 142 MB.
- **Chromium** downloads a dictionary only when the user picks a language, from one fixed URL. **Firefox, VS Code and LibreOffice** ship dictionaries as add-ons in their own stores. **HeliBoard** has no internet permission; users import dictionary files by hand.

### Proposed design

1. A "Languages" settings page lists the available languages and puts the game's language first. Minecraft reports codes like `de_de` or `pt_br` (`LanguageManager.getSelected()`). Map by prefix (`es_*` → Spanish), but keep `pt_pt` apart from `pt_br`.
2. Download only when the player presses the button, never at startup. Run it off the main thread with short timeouts.
3. Ask the Modrinth API for the language project's newest version that matches the mod's file format. Download from `cdn.modrinth.com`, write to a temporary file, check the SHA-512, then move it into `config/bettertyping/dictionaries/`. Never write to `mods/`.
4. Also load language jars that a launcher installed in `mods/`, so modpacks and offline players don't need the button.
5. The mod ships a small manifest naming each language's Modrinth project ID, plus an optional GitHub Release fallback URL with its hash.

## Where the words come from

### British English

The English Speller Database we already use has British spelling built in:

- `./scowl word-list 60 B 1` gives -ise British, 108,778 words. 2,861 of them aren't in the American list, e.g. "naturalisation".
- `60 B,Z 5` adds the -ize (Oxford) forms.
- Canadian (`C`) and Australian (`D`) lists are possible too. Australian needs Benjamin Titze's notice as well.
- Staying at size 70 or below keeps the UK Advanced Cryptics notice out, as for the American list.
- The same Google Books ranking works, so en_GB is just a second run of `tools/build_dictionary.py`.

### Other languages

The [LibreOffice dictionaries](https://github.com/LibreOffice/dictionaries) are current (last commit 2026-10-12) and say each language's licence in its README. They're hunspell files (`.dic` + `.aff`), so they must be expanded into word forms. [wooorm/dictionaries](https://github.com/wooorm/dictionaries) has some outdated licence metadata, so use LibreOffice.

Measured by expanding each dictionary (without generating compounds):

| Language | Licence (option to use) | Word forms | Flat list? |
|---|---|---|---|
| Spanish | GPL-3+ / LGPL-3+ / MPL-1.1+ | 657k (7.6 MB) | Yes |
| Portuguese (Brazil) | LGPL-3 / MPL | 10.5M, 2.7M without hyphenated clitic forms | Yes, capped |
| Portuguese (Portugal) | GPL-2 / LGPL-2.1 / MPL-1.1 | 1.04M, 413k without clitic forms | Yes |
| German | GPL-2 or GPL-3 | 366k | Yes, but valid compounds get flagged |
| French | MPL-2.0 | 3.03M, 452k without l'/d' forms | Yes, without elided forms |
| Italian | GPL-3.0 | 34.6M, 3.1M without elided forms | Yes, capped |
| Dutch | BSD-3 / CC BY 3.0 | 390k | Yes, but valid compounds get flagged |
| Swedish | LGPL-3.0 | 830k | Yes, but valid compounds get flagged |
| Russian | BSD-style | 1.44M (33 MB) | Needs a compact store |
| Polish | GPL / LGPL / MPL / Apache-2.0 | 3.52M (49 MB) | Needs a compact store |
| Turkish | MPL-2.0 | 1.36M | Needs a compact store |
| Ukrainian | MPL-1.1, but the underlying dict_uk data is CC BY-NC-SA | 3.41M | Check the licence first |
| Czech | GPL | 4.34M | Needs a compact store |
| Hungarian | MPL-2.0 / LGPL-3+ | about 0.9 billion with one suffix | No |
| Finnish | no hunspell dictionary; Voikko is GPL | — | No |
| Hebrew | AGPL-3.0 | about 109M | No |
| Arabic | GPL-2+ / LGPL-2.1+ / MPL-1.1 | about 42M | No |

Two sources are not good enough to use as the main source:

- **Keyboard dictionaries** (AOSP, HeliBoard) already come with frequencies. But the AOSP lists are frozen at 2014 with no documented source, and HeliBoard's licence metadata is incomplete. Use them for cross-checks only.
- **Wiktionary data** (kaikki.org) has inflection tables but no frequencies, and it's CC BY-SA.

## How to rank them

| Source | Languages | Text | Licence | Notes |
|---|---|---|---|---|
| [wordfreq](https://github.com/rspeer/wordfreq) | 44 | Subtitles, Twitter, Reddit, web, Wikipedia, books | CC BY-SA 4.0 | Frozen at about 2021 ([SUNSET.md](https://github.com/rspeer/wordfreq/blob/master/SUNSET.md)); lowercased |
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) | ~60 | Subtitles only | CC BY-SA 4.0 | Ranks "hello" high but misses internet slang; Ukrainian list contains Russian words |
| [Leipzig Corpora](https://wortschatz.uni-leipzig.de/en/usage) downloads | 250+ | News, web, Wikipedia | CC BY | Website and API data is non-commercial; only the downloads are CC BY |
| [Google Books Ngram](https://storage.googleapis.com/books/ngrams/books/datasetsv3.html) | en, fr, de, he, it, ru, es, zh | Books | CC BY 3.0 | Ranks chat words low, as we saw with English |

Where chat words land:

| Word | Subtitles | wordfreq | Keyboard list |
|---|---|---|---|
| hello (en) | 203 | 1,958 | 4,262 |
| hallo (de) | 164 | 762 | 70,700 |
| lol (en) | 18,339 | 959 | — |

wordfreq is the only source that mixes chat-like text across many languages on one scale, so it's the best fit for autocomplete. Its CC BY-SA 4.0 licence is the catch:

- A list ranked by it should be treated as CC BY-SA. That's the cautious reading; whether a ranking is protected at all is unclear.
- CC BY-SA 4.0 can only become GPL-3.0 ([compatibility](https://creativecommons.org/share-your-work/licensing-considerations/compatible-licenses/)). Every dictionary in the first two batches has a GPL-3.0-compatible option, so each language file would be **GPL-3.0**.
- The other way: rank with Leipzig (CC BY), which keeps each file under its dictionary's own licence but ranks chat words lower.

Words wordfreq hasn't seen are left out of the list, up to the cap of about 200k. wordfreq is lowercased, so case (German nouns) comes from the dictionary. Don't use non-commercial data: Leipzig's website data, HeliBoard's Slovak list, or some SUBTLEX lists. Mod-platform payouts could count as commercial use.

## Which languages first

Mojang's only published numbers are a [2016 chart of Java language settings](https://minecraft.wiki/w/File:Language_stats.jpg):

| Language | Share |
|---|---|
| en_US | 57.6% |
| en_GB | 7.0% |
| Spanish (all variants) | 4.7% |
| Russian | 4.3% |
| German | 4.0% |
| Japanese | 2.7% |
| French | 2.2% |
| Portuguese (Brazil) | 1.9% |
| Vietnamese | 1.2% |
| Italian | 1.05% |
| Polish | 0.95% |
| Swedish | 0.95% |
| Dutch | 0.94% |
| Turkish | 0.86% |

Newer traffic data (CurseForge, Modrinth) puts Brazil, Germany, Russia and Turkey near the top too. Popular mods get the most translations into Chinese, Brazilian Portuguese, French, Russian, Japanese, Ukrainian, Korean, Spanish and German.

Word lists can't work for some of these:

- Chinese, Japanese and Thai don't put spaces between words, and typing them in Java chat needs an IME mod.
- Korean puts particles on words, so a flat list covers too little.
- Vietnamese spaces separate syllables, so checks would only be per syllable.

How much running text the most common forms cover (measured on subtitle lists; English today is about 99.2% at 100k):

| Language | 100k | 200k | 500k |
|---|---|---|---|
| Spanish, Portuguese (Brazil), French | 98.8–99.2% | 99.4–99.7% | 99.8%+ |
| Italian, Dutch, Swedish | 98.4–99.0% | 99.1–99.5% | 99.6%+ |
| German | 98.3% | 99.0% | 99.6% |
| Polish, Russian, Czech | ~96.3% | ~98.0% | 99.2–99.4% |
| Turkish | 94.8% | 97.1% | 98.8% |
| Finnish, Hungarian | ~93.5% | ~95.8% | ~97.8% |

## Storage

Measured with the current `WordList`:

| Layout | Per word | 1M words | 5M words |
|---|---|---|---|
| Current `WordList` | ~205 B | ~205 MB | ~1 GB |
| `HashMap<String,Integer>` alone | ~118 B | ~115 MB | ~590 MB |
| Sorted `String[]` + `int[]` | ~68 B | ~65 MB | ~350 MB |
| morfologik automaton | under 1 B | — | 3.5 MB for 4.5M Polish forms |

- English today: 108k words, 21.8 MB of heap.
- A 200k Spanish list: 38.7 MB, suggestions in about 20 ms.
- 4.5M Polish forms: 921 MB, 7.9 s to load, and 240–480 ms per `suggest()`, because it compares the typo against every word of similar length.

So the first batch works as flat lists today. The second batch needs a compact word store: a [morfologik](https://github.com/morfologik/morfologik-stemming) automaton built offline from our ranked list (BSD licence, a 19 KB runtime jar, Java 11+), mapping each word to its rank. That's about 2–4 MB per language, and autocomplete walks the prefix and keeps the top results.

[Lucene's Hunspell](https://lucene.apache.org/core/10_5_0/analysis/common/org/apache/lucene/analysis/hunspell/Hunspell.html) (pure Java, Apache 2.0) is the only way to accept German compound words. It needs 6.5 MB of jars, has no frequencies, and can't list words by prefix. That's not worth it yet.

## Notices each download needs

- The licence text, and the upstream README with its copyright lines.
- A note of what was changed: expanded, filtered, ranked by wordfreq, the date and the upstream commit.
- Credit for wordfreq and its sources, including OpenSubtitles and SUBTLEX.
- For GPL, LGPL and MPL data, the source must be available: keep the pinned upstream files and the build script in this repo.
- Modrinth §4 asks for credit to each original source on the project page.

## Open questions

- Modrinth still returns 404 for Better Typing itself, so the mod's project may also still be waiting for approval.
- Ukrainian needs its licence checked before it can be offered.
