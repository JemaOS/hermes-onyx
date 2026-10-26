#!/usr/bin/env python3
"""Rend les orbes  sans perdre leurs détourages ni leurs dégradés.

Le convertisseur VectorDrawable précédent aplatissait les dégradés et supprimait les
clipPath : les rectangles noirs destinés à la vignette recouvraient donc tout l'orbe.
CairoSVG conserve ici le SVG complet. Le PNG RGBA 256 px reste net jusqu'à une tuile de
64 dp en densité xxxhdpi.

Usage :
  python3 scripts/render-orbs.py --source /chemin/onyx/design/icons/orbs
  python3 scripts/render-orbs.py --source /chemin/onyx/design/icons/orbs --check

Dépendance de génération seulement : ``cairosvg`` (aucune dépendance Android ajoutée).
"""

from __future__ import annotations

import argparse
import importlib
from pathlib import Path
import sys

cairosvg = importlib.import_module("cairosvg")


ORBS = {
    "ic_glasswave": "glassWave.svg",
    "ic_glasswaveblank": "glassWaveBlank.svg",
    "ic_flatwave": "flatWave.svg",
    "ic_flatwaveblank": "flatWaveBlank.svg",
    "ic_goldwave": "goldWave.svg",
    "ic_goldwaveblank": "goldWaveBlank.svg",
    "ic_inkwave": "inkWave.svg",
    "ic_inkwaveblank": "inkWaveBlank.svg",
    "ic_mattewave": "matteWave.svg",
    "ic_mattewaveblank": "matteWaveBlank.svg",
    "ic_navyglasswave": "navyGlassWave.svg",
    "ic_navyglasswaveblank": "navyGlassWaveBlank.svg",
    "ic_navymattewave": "navyMatteWave.svg",
    "ic_navymattewaveblank": "navyMatteWaveBlank.svg",
    "ic_steelglasswave": "steelGlassWave.svg",
    "ic_steelglasswaveblank": "steelGlassWaveBlank.svg",
    "ic_bubblewave": "bubbleWave.svg",
    "ic_bubblewaveblank": "bubbleWaveBlank.svg",
    "ic_bubblemattewave": "bubbleMatteWave.svg",
    "ic_bubblemattewaveblank": "bubbleMatteWaveBlank.svg",
    "ic_waveform": "waveform.svg",
    "ic_waveformblank": "waveformBlank.svg",
}


def render(source: Path) -> bytes:
    return cairosvg.svg2png(url=str(source), output_width=256, output_height=256)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True, help="Dossier design/icons/orbs ")
    parser.add_argument("--out", type=Path, default=root / "core/src/main/res/drawable")
    parser.add_argument("--check", action="store_true", help="Vérifie sans modifier les ressources")
    args = parser.parse_args()

    problems: list[str] = []
    for resource, filename in ORBS.items():
        source = args.source / filename
        if not source.is_file():
            problems.append(f"source absente: {source}")
            continue
        png = render(source)
        target = args.out / f"{resource}.png"
        vector = args.out / f"{resource}.xml"
        if args.check:
            if not target.is_file() or target.read_bytes() != png:
                problems.append(f"pixels obsolètes: {target}")
            if vector.exists():
                problems.append(f"ancien vector toujours présent: {vector}")
        else:
            args.out.mkdir(parents=True, exist_ok=True)
            target.write_bytes(png)
            vector.unlink(missing_ok=True)
            print(f"{filename} -> {target}")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    if args.check:
        print(f"OK : {len(ORBS)} orbes identiques aux SVG source, aucun ancien vector")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
