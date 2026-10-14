#!/usr/bin/env python3
"""Build a bundled dictionary file: one word per line, most frequent first.

Inputs:
  --esdb-lists  ESDB word lists generated with `./scowl word-list <size> A 1 --deaccent`,
                passed smallest size first. A word's size is the first list that contains it.
  --frequency   hackerb9/gwordlist frequency-alpha-alldicts.txt (Google Books counts).
  --max-size    largest ESDB size whose words are accepted as correct.

Words with a Google Books count are ordered by that count; the rest follow, ordered by ESDB size.
"""
import argparse
import re
import sys
from pathlib import Path

SIZE_RE = re.compile(r"wl-(\d+)\.txt$")


def read_esdb(paths):
    sizes = {}
    for path in paths:
        size = int(SIZE_RE.search(path.name).group(1))
        for line in path.read_text(encoding="utf-8").splitlines():
            word = line.strip()
            if word and word not in sizes:
                sizes[word] = size
    return sizes


def read_frequency_ranks(path):
    ranks = {}
    with path.open(encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            word = parts[1].lower()
            ranks.setdefault(word, int(parts[0]))
    return ranks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--esdb-lists", nargs="+", type=Path, required=True)
    parser.add_argument("--frequency", type=Path, required=True)
    parser.add_argument("--max-size", type=int, default=60)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    sizes = {w: s for w, s in read_esdb(args.esdb_lists).items() if s <= args.max_size}
    ranks = read_frequency_ranks(args.frequency)
    unranked = len(ranks) + 1

    words = sorted(sizes, key=lambda w: (ranks.get(w.lower(), unranked), sizes[w], w))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(words) + "\n", encoding="utf-8")

    ranked = sum(1 for w in words if w.lower() in ranks)
    print(f"{len(words)} words ({ranked} with frequency) -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
