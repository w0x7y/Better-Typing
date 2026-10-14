"""Draws the mod icon: "Aa" with the red spelling squiggle, as 16x16 pixel art scaled up without smoothing.

Writes assets/icon.png (512 px, for Modrinth) and the 128 px icon that ships in the jar for Mod Menu.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SIZE = 16
BACKGROUND = (0x18, 0x18, 0x1F, 255)
TEXT = (255, 255, 255, 255)
SQUIGGLE = (255, 85, 85, 255)  # the same red as the in-game underline

UPPER_A = [
    ".###.",
    "#...#",
    "#####",
    "#...#",
    "#...#",
    "#...#",
    "#...#",
]
LOWER_A = [
    ".....",
    ".....",
    ".###.",
    "....#",
    ".####",
    "#...#",
    ".####",
]


def glyph(image: Image.Image, rows: list[str], x: int, y: int, color: tuple) -> None:
    for dy, row in enumerate(rows):
        for dx, cell in enumerate(row):
            if cell == "#":
                image.putpixel((x + dx, y + dy), color)


def draw() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), BACKGROUND)
    # No text shadow: at this size it fills the gap between the letters.
    for rows, x in [(UPPER_A, 2), (LOWER_A, 9)]:
        glyph(image, rows, x, 3, TEXT)
    # Two pixels up, two down, like the underline drawn under misspelled words.
    for x in range(2, 14):
        image.putpixel((x, 11 + (((x - 2) >> 1) & 1)), SQUIGGLE)
    return image


def main() -> None:
    icon = draw()
    outputs = {
        ROOT / "assets/icon.png": 512,
        ROOT / "common/src/main/resources/assets/bettertyping/icon.png": 128,
    }
    for path, size in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        icon.resize((size, size), Image.NEAREST).save(path, optimize=True)
        print(f"Wrote {path.relative_to(ROOT)} ({size} px)")


if __name__ == "__main__":
    main()
