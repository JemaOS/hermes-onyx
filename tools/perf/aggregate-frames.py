#!/usr/bin/env python3
"""Agrège plusieurs passes de `framestats` et sort les percentiles par étage.

Android ne garde que les **120 dernières trames** dans son tampon `framestats`
(`FrameStats.MAX_FRAMESTATS`). Une passe de dix glissements produit des centaines de
trames : le tampon ne retient que la **fin** du geste, et la médiane mesurée n'est donc
pas celle du défilement. Ce script agrège **plusieurs passes courtes**, chacune vidée
avant son geste, pour que chaque trame du geste soit conservée.

usage : aggregate-frames.py <sortie> <fichier1> [fichier2 ...]
"""
import glob
import sys


def read_rows(path):
    with open(path, encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    lines = text.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line.startswith("Flags,"):
            start = index
            break
    if start is None:
        return []
    header = lines[start].split(",")
    rows = []
    for line in lines[start + 1:]:
        if not line or not line[0].isdigit():
            continue
        values = line.split(",")
        if len(values) < len(header):
            continue
        row = {}
        for name, value in zip(header, values):
            try:
                row[name] = int(value)
            except ValueError:
                row[name] = 0
        rows.append(row)
    return rows


def main():
    out_path = sys.argv[1]
    files = []
    for pattern in sys.argv[2:]:
        files.extend(sorted(glob.glob(pattern)))
    rows = []
    for path in files:
        rows.extend(read_rows(path))
    if not rows:
        print("aucune trame lue dans %s" % ", ".join(files))
        return

    # On ne garde que les trames qui ont commencé et terminé.
    drawn = [r for r in rows if r.get("IntendedVsync", 0) > 0 and r.get("FrameCompleted", 0) > 0]

    def deltas(start, end):
        found = []
        for row in drawn:
            a = row.get(start, 0)
            b = row.get(end, 0)
            if a > 0 and b > a:
                found.append(b - a)
        return found

    def quantile(values, fraction):
        values = sorted(values)
        count = len(values)
        if not count:
            return 0.0
        return values[min(count - 1, int(count * fraction))] / 1e6

    stages = (
        ("UI thread (input -> sync)", "HandleInputStart", "SyncStart"),
        ("compose+measure+layout+record", "PerformTraversalsStart", "SyncStart"),
        ("flush des commandes", "SyncStart", "SwapBuffers"),
        ("GPU / pilote", "SwapBuffers", "GpuCompleted"),
        ("trame complete", "IntendedVsync", "FrameCompleted"),
    )

    lines = []
    lines.append("trames retenues : %d (sur %d lues)" % (len(drawn), len(rows)))
    for label, start, end in stages:
        values = deltas(start, end)
        if not values:
            continue
        lines.append(
            "  %-34s p50=%7.2f  p90=%7.2f  p99=%7.2f  max=%7.2f ms"
            % (
                label,
                quantile(values, 0.50),
                quantile(values, 0.90),
                quantile(values, 0.99),
                max(values) / 1e6,
            )
        )

    for budget, label in ((16.67, "60 Hz"), (8.33, "120 Hz")):
        spans = [
            row["FrameCompleted"] - row["IntendedVsync"]
            for row in drawn
            if row.get("FrameCompleted", 0) > row.get("IntendedVsync", 0) > 0
        ]
        if not spans:
            continue
        over = sum(1 for span in spans if span / 1e6 > budget)
        lines.append(
            "  trames > %5.2f ms (%s) : %d / %d (%.1f %%)"
            % (budget, label, over, len(spans), 100 * over / len(spans))
        )

    text = "\n".join(lines)
    print(text)
    if out_path:
        with open(out_path, "w", encoding="utf-8") as handle:
            handle.write(text + "\n")


if __name__ == "__main__":
    main()
