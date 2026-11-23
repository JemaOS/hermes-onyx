#!/usr/bin/env python3
"""Builds the app icons (48-192 px mipmaps, square and round) from the vector
source in `design/icons/`.

The mark is Onyx's "O", drawn like the Othalan rune (ᛟ): a closed diamond and its
two legs crossing below. It is a geometric path (`Path`), not a font
or an emoji: it stays sharp at 24 dp as well as at 1024 px. The source `design/icons/*.svg` carries the
outline; this script rasterizes it at 1024 px (headless Chromium) then scales it down in LANCZOS
to the five Android densities.

Usage:
    python3 tools/make-app-icons.py                 # all variants
    python3 tools/make-app-icons.py --preview DIR    # + a 512 px preview per variant
    python3 tools/make-app-icons.py --android-path   # the pathData for the vector drawables
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "design" / "icons"
RES = ROOT / "app" / "src" / "main" / "res"
WORK = Path("/home/john/icontmp-work")   # outside the cache folder: Chromium (snap) must read the SVG

CHROMIUM = "/snap/bin/chromium"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# --- the Othalan rune, in units of the 64 x 64 view ---------------------------------------
RUNE_CENTER = (32.0, 31.0)   # optical center of the bubble, where the mark used to sit
RUNE_SIZE = (13.2, 18.6)     # visual width, height (diamond and legs included)
RUNE_STROKE = 3.0            # same weight as the stem of the "r" in the reference
RUNE_SIDE = 0.36             # height of the side vertices, as a fraction of the apex


def rune_points(cx: float, cy: float, width: float, height: float, stroke: float):
    """The five vertices, plainly: left foot, right vertex, apex, left vertex, right foot."""
    wc = width - stroke
    hc = height - stroke
    top = cy - hc / 2
    left_x, right_x = cx - wc / 2, cx + wc / 2
    side_y = top + RUNE_SIDE * hc
    bottom = cy + hc / 2
    return [
        (left_x, bottom),    # left foot
        (right_x, side_y),   # right vertex
        (cx, top),           # apex
        (left_x, side_y),    # left vertex
        (right_x, bottom),   # right foot
    ]


def android_path(cx: float = RUNE_CENTER[0], cy: float = RUNE_CENTER[1],
                 width: float = RUNE_SIZE[0], height: float = RUNE_SIZE[1],
                 stroke: float = RUNE_STROKE) -> str:
    pts = rune_points(cx, cy, width, height, stroke)
    (x0, y0), *rest = pts
    d = f"M{x0:.2f},{y0:.2f}"
    d += "".join(f"L{x:.2f},{y:.2f}" for x, y in rest)
    d += "Z"   # closes the shape: the last stroke returns to the left foot
    return d


# --- Android variant -> vector source ------------------------------------------------------
VARIANTS: dict[str, Path] = {
    "face_mist": DESIGN / "picker" / "onyx-face-mist.svg",
    "graphite": DESIGN / "picker" / "graphite.svg",
    "graphite_block": DESIGN / "picker" / "graphite-block.svg",
    "steel_block": DESIGN / "picker" / "steel-block.svg",
    "mist": DESIGN / "picker" / "mist-graphite.svg",
    "graphite_flat": DESIGN / "picker" / "graphite-flat.svg",
    "graphite_block_flat": DESIGN / "picker" / "graphite-block-flat.svg",
    "steel_flat": DESIGN / "picker" / "steel-block-flat.svg",
    "mist_flat": DESIGN / "picker" / "mist-graphite-flat.svg",
    "navy_block": DESIGN / "picker" / "navy-block.svg",
    "ink": DESIGN / "picker" / "ink-block.svg",
    "paper_navy": DESIGN / "picker" / "paper-navy.svg",
    "gold": DESIGN / "picker" / "gold-graphite.svg",
    "classic": DESIGN / "picker" / "classic.svg",
}


def render(svg: Path, out: Path) -> Path:
    WORK.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [CHROMIUM, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
         "--default-background-color=00000000", "--window-size=1024,1024",
         f"--screenshot={out}", f"file://{svg}"],
        check=True, capture_output=True,
    )
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--preview")
    ap.add_argument("--android-path", action="store_true")
    args = ap.parse_args()

    if args.android_path:
        print(android_path())
        return 0
    if not Path(CHROMIUM).exists():
        print("Chromium introuvable :", CHROMIUM, file=sys.stderr)
        return 1

    missing = [n for n, p in VARIANTS.items() if not p.is_file()]
    if missing:
        print("sources manquantes :", ", ".join(missing), file=sys.stderr)
        return 1

    for name, src in VARIANTS.items():
        if args.only and args.only != name:
            continue
        big = render(src, WORK / f"{name}-1024.png")
        plate = Image.open(big).convert("RGB")
        if args.preview:
            Path(args.preview).mkdir(parents=True, exist_ok=True)
            plate.resize((512, 512), Image.LANCZOS).save(Path(args.preview) / f"{name}.png")
        for dens, px in DENSITIES.items():
            out = plate.resize((px, px), Image.LANCZOS)
            d = RES / f"mipmap-{dens}"
            d.mkdir(parents=True, exist_ok=True)
            out.save(d / f"ic_launcher_{name}.png")
            out.save(d / f"ic_launcher_{name}_round.png")
        print("ecrit", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
