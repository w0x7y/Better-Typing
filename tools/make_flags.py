"""Draws the flags shown next to each language in the settings, as 16x11 pixel art, and the font that holds them.

A language's flag is the country in its code (de_de: Germany, ar_sa: Saudi Arabia). The font
bettertyping:flags gives each country a private-use character, U+E000 + 26 * first letter + second
letter (de: U+E052), and the globe, for the game language, U+E2A4. Everything else falls through to
the default font, so a whole label can use it. LanguagePacks.Language.flag works out the same characters.

Writes common/src/main/resources/assets/bettertyping/textures/font/flag/<country>.png and font/flags.json.
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "common/src/main/resources/assets/bettertyping"
SIZE = 16
TOP = 1  # drawn at half size, the 11 rows sit level with the letters
GLOBE = 0xE2A4

COLORS = {
    "world": {"B": 0x5B92E5, "W": 0xFFFFFF},
    "us": {"R": 0xB22234, "W": 0xFFFFFF, "B": 0x3C3B6E},
    "gb": {"R": 0xC8102E, "W": 0xFFFFFF, "B": 0x012169},
    "sa": {"G": 0x006C35, "W": 0xFFFFFF},
    "de": {"K": 0x000000, "R": 0xDD0000, "Y": 0xFFCE00},
    "es": {"R": 0xAA151B, "Y": 0xF1BF00},
    "fr": {"B": 0x0055A4, "W": 0xFFFFFF, "R": 0xEF4135},
    "il": {"B": 0x0038B8, "W": 0xFFFFFF},
    "it": {"G": 0x009246, "W": 0xFFFFFF, "R": 0xCE2B37},
    "nl": {"R": 0xAE1C28, "W": 0xFFFFFF, "B": 0x21468B},
    "br": {"G": 0x009C3B, "Y": 0xFFDF00, "B": 0x002776},
    "pt": {"G": 0x006600, "R": 0xFF0000, "Y": 0xFFFF00, "W": 0xFFFFFF},
    "ru": {"W": 0xFFFFFF, "B": 0x0039A6, "R": 0xD52B1E},
    "se": {"B": 0x006AA7, "Y": 0xFECC02},
}


def stripes(*rows: tuple[str, int]) -> list[str]:
    """Horizontal stripes, each a color and a height in rows."""
    return [color * SIZE for color, height in rows for _ in range(height)]


def bars(*columns: tuple[str, int]) -> list[str]:
    """Vertical bars, each a color and a width in columns."""
    return ["".join(color * width for color, width in columns)] * 11


FLAGS = {
    "world": [
        "BBBBBBBBBBBBBBBB",
        "BBBBBBWWWWBBBBBB",
        "BBBBBWBWWBWBBBBB",
        "BBBBWBWBBWBWBBBB",
        "BBBBWBWBBWBWBBBB",
        "BBBBWWWWWWWWBBBB",
        "BBBBWBWBBWBWBBBB",
        "BBBBWBWBBWBWBBBB",
        "BBBBBWBWWBWBBBBB",
        "BBBBBBWWWWBBBBBB",
        "BBBBBBBBBBBBBBBB",
    ],
    "us": [
        "BBBBBBBRRRRRRRRR",
        "BWBWBWBWWWWWWWWW",
        "BBWBWBBRRRRRRRRR",
        "BWBWBWBWWWWWWWWW",
        "BBWBWBBRRRRRRRRR",
        "BBBBBBBWWWWWWWWW",
        "RRRRRRRRRRRRRRRR",
        "WWWWWWWWWWWWWWWW",
        "RRRRRRRRRRRRRRRR",
        "WWWWWWWWWWWWWWWW",
        "RRRRRRRRRRRRRRRR",
    ],
    "gb": [
        "RWBBBBWRRWBBBBWR",
        "BWRWBBWRRWBBWRWB",
        "BBBWRWWRRWWRWBBB",
        "BBBBWRWRRWRWBBBB",
        "WWWWWWWRRWWWWWWW",
        "RRRRRRRRRRRRRRRR",
        "WWWWWWWRRWWWWWWW",
        "BBBBWRWRRWRWBBBB",
        "BBBWRWWRRWWRWBBB",
        "BWRWBBWRRWBBWRWB",
        "RWBBBBWRRWBBBBWR",
    ],
    "sa": [
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGWGWGWWGWGWGGG",
        "GGGWWWWWWWWWWGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGWGGGG",
        "GGGWWWWWWWWWWWGG",
        "GGGGGGGGGGGWGGGG",
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGGGGGGGGGG",
    ],
    "de": stripes(("K", 4), ("R", 3), ("Y", 4)),
    "es": [
        "RRRRRRRRRRRRRRRR",
        "RRRRRRRRRRRRRRRR",
        "RRRRRRRRRRRRRRRR",
        "YYYYYYYYYYYYYYYY",
        "YYYRRRYYYYYYYYYY",
        "YYYRRRYYYYYYYYYY",
        "YYYYRYYYYYYYYYYY",
        "YYYYYYYYYYYYYYYY",
        "RRRRRRRRRRRRRRRR",
        "RRRRRRRRRRRRRRRR",
        "RRRRRRRRRRRRRRRR",
    ],
    "fr": bars(("B", 5), ("W", 6), ("R", 5)),
    "il": [
        "WWWWWWWWWWWWWWWW",
        "BBBBBBBBBBBBBBBB",
        "WWWWWWWWWWWWWWWW",
        "WWWWWWWBBWWWWWWW",
        "WWWWWBBBBBBWWWWW",
        "WWWWWWBWWBWWWWWW",
        "WWWWWBBBBBBWWWWW",
        "WWWWWWWBBWWWWWWW",
        "WWWWWWWWWWWWWWWW",
        "BBBBBBBBBBBBBBBB",
        "WWWWWWWWWWWWWWWW",
    ],
    "it": bars(("G", 5), ("W", 6), ("R", 5)),
    "nl": stripes(("R", 4), ("W", 3), ("B", 4)),
    "br": [
        "GGGGGGGGGGGGGGGG",
        "GGGGGGGYYGGGGGGG",
        "GGGGGYYYYYYGGGGG",
        "GGGGYYBBBBYYGGGG",
        "GGYYYBBBBBBYYYGG",
        "GYYYYBBBBBBYYYYG",
        "GGYYYBBBBBBYYYGG",
        "GGGGYYBBBBYYGGGG",
        "GGGGGYYYYYYGGGGG",
        "GGGGGGGYYGGGGGGG",
        "GGGGGGGGGGGGGGGG",
    ],
    "pt": [
        "GGGGGGRRRRRRRRRR",
        "GGGGGGRRRRRRRRRR",
        "GGGGGGRRRRRRRRRR",
        "GGGGGYYYRRRRRRRR",
        "GGGGYRRRYRRRRRRR",
        "GGGGYRWRYRRRRRRR",
        "GGGGYRRRYRRRRRRR",
        "GGGGGYYYRRRRRRRR",
        "GGGGGGRRRRRRRRRR",
        "GGGGGGRRRRRRRRRR",
        "GGGGGGRRRRRRRRRR",
    ],
    "ru": stripes(("W", 4), ("B", 3), ("R", 4)),
    "se": [
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
        "YYYYYYYYYYYYYYYY",
        "YYYYYYYYYYYYYYYY",
        "YYYYYYYYYYYYYYYY",
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
        "BBBBBYYYBBBBBBBB",
    ],
}


def draw(name: str) -> Image.Image:
    rows = FLAGS[name]
    assert len(rows) == 11 and all(len(row) == SIZE for row in rows), name
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            rgb = COLORS[name][cell]
            image.putpixel((x, TOP + y), (rgb >> 16, (rgb >> 8) & 0xFF, rgb & 0xFF, 255))
    return image


def character(name: str) -> str:
    if name == "world":
        return chr(GLOBE)
    return chr(0xE000 + 26 * (ord(name[0]) - ord("a")) + ord(name[1]) - ord("a"))


def main() -> None:
    images = ASSETS / "textures/font/flag"
    images.mkdir(parents=True, exist_ok=True)
    providers = []
    for name in FLAGS:
        draw(name).save(images / f"{name}.png", optimize=True)
        providers.append({"type": "bitmap", "file": f"bettertyping:font/flag/{name}.png", "height": 8, "ascent": 7, "chars": [character(name)]})
    providers.append({"type": "reference", "id": "minecraft:default"})
    lines = ",\n\t\t".join(json.dumps(provider) for provider in providers)
    (ASSETS / "font").mkdir(exist_ok=True)
    (ASSETS / "font/flags.json").write_text('{\n\t"providers": [\n\t\t' + lines + "\n\t]\n}\n", encoding="utf-8")


if __name__ == "__main__":
    main()
