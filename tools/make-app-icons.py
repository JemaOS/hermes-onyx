#!/usr/bin/env python3
"""Fabrique les icones de l'app (mipmaps 48-192 px, carre et rond) depuis la source
vectorielle de `design/icons/`.

La marque est le « O » d'Onyx, trace comme la rune Othalan (ᛟ) : un losange ferme et ses
deux jambes qui se croisent au-dessous. C'est un chemin geometrique (`Path`), pas une police
ni un emoji : il reste net a 24 dp comme a 1024 px. La source `design/icons/*.svg` porte le
tracé ; ce script la rasterise en 1024 px (Chromium sans interface) puis la reduit en LANCZOS
aux cinq densites Android.

Usage :
    python3 tools/make-app-icons.py                 # toutes les variantes
    python3 tools/make-app-icons.py --preview DIR    # + un apercu 512 px par variante
    python3 tools/make-app-icons.py --android-path   # le pathData pour les vector drawables
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
WORK = Path("/home/john/icontmp-work")   # hors dossier cache : Chromium (snap) doit lire le SVG

CHROMIUM = "/snap/bin/chromium"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# --- la rune Othalan, en unites de la vue 64 x 64 -----------------------------------------
RUNE_CENTER = (32.0, 31.0)   # centre optique de la bulle, la ou se tenait la marque
RUNE_SIZE = (13.2, 18.6)     # largeur, hauteur visuelles (losange et jambes compris)
RUNE_STROKE = 3.0            # meme graisse que la hampe du « r » de la reference
RUNE_SIDE = 0.36             # hauteur des sommets lateraux, en part du faite


def rune_points(cx: float, cy: float, width: float, height: float, stroke: float):
    """Les cinq sommets, en clair : pied gauche, sommet droit, faite, sommet gauche, pied droit."""
    wc = width - stroke
    hc = height - stroke
    top = cy - hc / 2
    left_x, right_x = cx - wc / 2, cx + wc / 2
    side_y = top + RUNE_SIDE * hc
    bottom = cy + hc / 2
    return [
        (left_x, bottom),    # pied gauche
        (right_x, side_y),   # sommet droit
        (cx, top),           # faite
        (left_x, side_y),    # sommet gauche
        (right_x, bottom),   # pied droit
    ]


def android_path(cx: float = RUNE_CENTER[0], cy: float = RUNE_CENTER[1],
                 width: float = RUNE_SIZE[0], height: float = RUNE_SIZE[1],
                 stroke: float = RUNE_STROKE) -> str:
    pts = rune_points(cx, cy, width, height, stroke)
    (x0, y0), *rest = pts
    d = f"M{x0:.2f},{y0:.2f}"
    d += "".join(f"L{x:.2f},{y:.2f}" for x, y in rest)
    d += "Z"   # referme la figure : le dernier trait revient au pied gauche
    return d


# --- variante Android -> source vectorielle ------------------------------------------------
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
