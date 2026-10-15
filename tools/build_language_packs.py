#!/usr/bin/env python3
"""Build the downloadable language packs listed in tools/language_packs.json.

Each pack is a small Fabric mod jar holding assets/bettertyping/dictionaries/<code>.txt: the words of
wordfreq's list for that language (most common first) that the LibreOffice dictionary accepts, up
to max_words. French and Italian also list the elisions their dictionary allows (l', dell'), so
"l'homme" counts as correct. The jar carries the GPL-3.0 text, a NOTICE and the upstream readmes.

Needs the pinned wordfreq and spylls versions (pip install wordfreq==X spylls==Y) and the
LibreOffice dictionaries files at the pinned commit, e.g. from
https://raw.githubusercontent.com/LibreOffice/dictionaries/<commit>/<path>. Every file is checked
against its SHA-256 in the config. Prints one line per pack for the mod's language catalog.
"""
import argparse
import hashlib
import importlib.metadata
import json
import multiprocessing
import subprocess
import sys
import textwrap
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "tools/language_packs.json"
REPOSITORY = "https://github.com/w0x7y/Better-Typing"
ZIP_TIME = (1980, 1, 1, 0, 0, 0)

WORDFREQ_NOTICE = """\
wordfreq is freely redistributable under the Apache license, and it includes data files that may be
redistributed under a Creative Commons Attribution-ShareAlike 4.0 license
(https://creativecommons.org/licenses/by-sa/4.0/).

wordfreq contains data extracted from Google Books Ngrams (http://books.google.com/ngrams) and
Google Books Syntactic Ngrams
(http://commondatastorage.googleapis.com/books/syntactic-ngrams/index.html).

wordfreq also contains data derived from the following Creative Commons-licensed sources: the Leeds
Internet Corpus, from the University of Leeds Centre for Translation Studies
(http://corpus.leeds.ac.uk/list.html); Wikipedia, the free encyclopedia (http://www.wikipedia.org);
and ParaCrawl, a multilingual Web crawl (https://paracrawl.eu).

It contains data from OPUS OpenSubtitles 2018 (http://opus.nlpl.eu/OpenSubtitles.php), whose data
originates from the OpenSubtitles project (http://www.opensubtitles.org/).

It contains data from various SUBTLEX word lists: SUBTLEX-US, SUBTLEX-UK, SUBTLEX-CH, SUBTLEX-DE and
SUBTLEX-NL, created by Marc Brysbaert et al. and available at
http://crr.ugent.be/programs-data/subtitle-frequencies. SUBTLEX is freely available data.

Some additional data was collected by watching the streaming Twitter API. wordfreq gives
statistics about words that are commonly used on Twitter; it does not display or republish any
Twitter content.

Robyn Speer. (2022). rspeer/wordfreq: v3.0 (v3.0.2). Zenodo. https://doi.org/10.5281/zenodo.7199437
"""


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_sources(config, sources):
    for name, expected in config["files"].items():
        path = sources / name
        if not path.is_file():
            raise SystemExit(f"Missing {path}")
        if sha256(path) != expected:
            raise SystemExit(f"{path} doesn't match its SHA-256 in {CONFIG.name}")
    for package in ("wordfreq", "spylls"):
        installed = importlib.metadata.version(package)
        if installed != config[f"{package}_version"]:
            raise SystemExit(f"{package} {installed} is installed, the config pins {config[f'{package}_version']}")


def elisions(aff_path):
    """Prefixes ending in an apostrophe that the dictionary allows before a word, lowercase."""
    found = set()
    for line in aff_path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split()
        if len(parts) >= 5 and parts[0] == "PFX" and parts[3].endswith("'"):
            found.add(parts[3].lower())
    return sorted(found)


def words(code, language, sources, max_words):
    """The pack's word list: wordfreq's words the dictionary accepts, most common first."""
    import wordfreq
    from spylls.hunspell import Dictionary

    dictionaries = [Dictionary.from_files(str(sources / base)) for base in language["hunspell"]]

    def accepted(form):
        return any(d.lookup(form) for d in dictionaries)

    kept = []
    seen = set()
    for token in wordfreq.iter_wordlist(language["wordfreq"], wordlist="best"):
        # The mod doesn't check single letters or words with digits (wordfreq writes digits as 0).
        if len(token) < 2 or any(c.isdigit() for c in token) or not any(c.isalpha() for c in token):
            continue
        # Lowercase covers the capitalized and all-caps forms too, so it's tried first.
        for form in (token, token[:1].upper() + token[1:], token.upper()):
            if accepted(form):
                break
        else:
            # Italian "po'" is checked without its apostrophe, as the mod strips apostrophes at word edges.
            form = token if accepted(token + "'") else None
        if form and form not in seen:
            seen.add(form)
            kept.append(form)
            if len(kept) >= max_words:
                break
    print(f"{code}: {len(kept)} words", file=sys.stderr, flush=True)
    return kept


def wrap(text, indent=""):
    return textwrap.fill(text, width=100, initial_indent=indent, subsequent_indent=" " * len(indent))


def notice(code, language, config, count, elided, commit):
    hunspell_files = [f"{base}.{ext}" for base in language["hunspell"] for ext in ("aff", "dic")]
    files = "\n".join(f"   {name}  sha256 {config['files'][name]}" for name in hunspell_files)
    steps = [wrap(
        f"Every word in wordfreq's {language['english']} list was checked against the LibreOffice dictionary, "
        f"most common first. Words it accepts were kept: in lowercase when the dictionary allows it, otherwise "
        f"capitalized or in capitals. Numbers and single letters were left out, and the list stops at "
        f"{config['max_words']:,} words. It ends up with {count:,}.", "1. ")]
    if elided:
        steps.append(wrap(
            f"The list ends with the elisions the dictionary allows: {', '.join(elided)}. A word of the list "
            f"with one of them in front counts as correct too.", "2. "))
    return f"""\
Better Typing: {language['english']} word list ({code}), pack {config['pack_version']}
{'=' * 60}

assets/bettertyping/dictionaries/{code}.txt holds {language['english']} words for Better Typing's spell
check and autocomplete, one per line, most common first.

Licence: GNU General Public License version 3 only (GPL-3.0-only), in LICENSE.

How it was made
---------------

{chr(10).join(steps)}

Built by tools/build_language_packs.py from {REPOSITORY}, commit {commit}.


1. Words: LibreOffice dictionaries
----------------------------------

https://github.com/LibreOffice/dictionaries, commit {config['libreoffice_commit']} ({config['libreoffice_date']})
{files}

{wrap(language['credit'])}

The upstream readme and licence files are in upstream/, unchanged.


2. Word order: wordfreq {config['wordfreq_version']}
{'-' * 30}

wordfreq by Robyn Speer, https://github.com/rspeer/wordfreq. Only its word frequencies were used, to
choose the words and put them in order; no counts are included.

{WORDFREQ_NOTICE}"""


def write_jar(path, entries):
    """A jar with fixed timestamps and order, so the same input gives the same bytes."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            jar.writestr(info, entries[name])


def build(code, config, sources, out, commit):
    language = config["languages"][code]
    kept = words(code, language, sources, config["max_words"])
    elided = sorted({e for base in language["hunspell"] for e in elisions(sources / f"{base}.aff")})
    text = "\n".join(kept + elided) + "\n"
    (out / f"{code}.txt").write_text(text, encoding="utf-8")

    mod = {
        "schemaVersion": 1,
        "id": f"bettertyping_{code}",
        "version": config["pack_version"],
        "name": f"Better Typing: {language['english']}",
        "description": f"{language['english']} words for Better Typing's spell check and autocomplete.",
        "authors": ["Idan Gilboa"],
        "contact": {"sources": REPOSITORY},
        "license": "GPL-3.0-only",
        "environment": "client",
        "depends": {"bettertyping": ">=0.2.0"},
    }
    entries = {
        "fabric.mod.json": json.dumps(mod, indent=2, ensure_ascii=False) + "\n",
        f"assets/bettertyping/dictionaries/{code}.txt": text,
        "LICENSE": (sources / config["gpl_text"]).read_bytes(),
        "NOTICE.txt": notice(code, language, config, len(kept), elided, commit),
    }
    for name in language["upstream"]:
        entries[f"upstream/{Path(name).name}"] = (sources / name).read_bytes()
    jar = out / f"bettertyping-{code}-{config['pack_version']}.jar"
    write_jar(jar, {name: data.encode("utf-8") if isinstance(data, str) else data for name, data in entries.items()})
    data = jar.read_bytes()
    return code, jar.name, len(data), hashlib.sha512(data).hexdigest(), len(kept)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sources", type=Path, required=True, help="folder holding the LibreOffice dictionaries files")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--only", nargs="*", help="language codes to build, e.g. de_de")
    args = parser.parse_args()

    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    check_sources(config, args.sources)
    commit = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    codes = args.only or list(config["languages"])
    args.out.mkdir(parents=True, exist_ok=True)
    with multiprocessing.Pool(len(codes)) as pool:
        results = pool.starmap(build, [(code, config, args.sources, args.out, commit) for code in codes])
    for code, name, size, digest, count in results:
        print(f"{code}\t{config['languages'][code]['name']}\t{name}\t{size}\t{digest}")


if __name__ == "__main__":
    main()
