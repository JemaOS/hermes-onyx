#!/usr/bin/env bash
# Mesure de défilement d'Onyx : N glissements haut puis bas, puis les statistiques de trames.
#
# usage : measure-scroll.sh <tag> [glissements] [paquet]
#   ADB   chemin d'adb (défaut : celui du SDK local, sinon `adb` du PATH)
#   SERIAL émulateur/appareil ciblé, si plusieurs sont branchés
#
# Sortie : $OUT/<tag>-gfxinfo.txt, $OUT/<tag>-framestats.txt, $OUT/<tag>.png
# puis, sur la sortie standard, le pourcentage de trames saccadées et le temps de thread UI.
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/android-toolchain/sdk/platform-tools/adb")}
PKG=${3:-com.onyx.app}
TAG=${1:-mesure}
N=${2:-14}
OUT=${OUT:-$(pwd)/perf-out}
mkdir -p "$OUT"
ADB="$ADB ${SERIAL:+-s $SERIAL}"

# L'app repart du même état : accueil, puis la conversation la plus récente.
$ADB shell am force-stop "$PKG"
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
# La carte « Continuer » de l'accueil, au centre de l'écran (1080×2400).
$ADB shell input tap 540 1098
sleep 6

$ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
for _ in $(seq 1 "$N"); do
  $ADB shell input swipe 540 1900 540 700 90
  sleep 0.25
done
for _ in $(seq 1 "$N"); do
  $ADB shell input swipe 540 700 540 1900 90
  sleep 0.25
done
sleep 2

$ADB shell dumpsys gfxinfo "$PKG" > "$OUT/$TAG-gfxinfo.txt"
$ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$TAG-framestats.txt"
$ADB exec-out screencap -p > "$OUT/$TAG.png"

python3 - "$OUT/$TAG-gfxinfo.txt" "$OUT/$TAG-framestats.txt" "$TAG" <<'PY'
import re, sys

def summary(path, tag):
    text = open(path, encoding="utf-8", errors="replace").read()
    def num(label):
        m = re.search(label + r"[^0-9]*([0-9]+)", text)
        return int(m.group(1)) if m else None
    total, janky = num("Total frames rendered"), num("Janky frames:")
    if total:
        share = f"{100 * janky / total:.1f} %" if janky is not None else "?"
        print(f"{tag} : {total} trames, {janky} saccadées ({share})")
    for p in ("50th", "90th", "95th", "99th"):
        m = re.search(p + r" percentile:\s*([0-9]+)ms", text)
        if m:
            print(f"  {p} percentile : {m.group(1)} ms")
    for label in ("Number Slow UI thread", "Number Slow issue draw commands", "Number Missed Vsync"):
        m = re.search(label + r":\s*([0-9]+)", text)
        if m:
            print(f"  {label} : {m.group(1)}")

def ui_times(path, tag):
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    headers = [i for i, l in enumerate(lines) if l.startswith("Flags,")]
    if not headers:
        return
    cols = lines[headers[0]].split(",")
    ui, total = [], []
    started = False
    for line in lines[headers[0] + 1:]:
        if line and line[0].isdigit():
            started = True
            values = line.split(",")
            if len(values) < len(cols):
                continue
            row = dict(zip(cols, [int(v) if v.lstrip("-").isdigit() else 0 for v in values]))
            if row.get("HandleInputStart", 0) > 0 and row.get("SyncStart", 0) > 0:
                ui.append(row["SyncStart"] - row["HandleInputStart"])
            if row.get("IntendedVsync", 0) > 0 and row.get("FrameCompleted", 0) > 0:
                total.append(row["FrameCompleted"] - row["IntendedVsync"])
        elif started:
            break
    def quantiles(values, label):
        if not values:
            return
        values.sort()
        n = len(values)
        pick = lambda p: values[min(n - 1, int(n * p))] / 1e6
        print(f"  {label} : n={n} p50={pick(.5):.1f} p90={pick(.9):.1f} p99={pick(.99):.1f} ms")
    quantiles(ui, "thread UI (input→sync)")
    quantiles(total, "trame totale")

summary(sys.argv[1], sys.argv[3])
ui_times(sys.argv[2], sys.argv[3])
PY
