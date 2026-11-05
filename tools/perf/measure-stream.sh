#!/usr/bin/env bash
# Mesure d'une réponse qui s'écrit : la scène « beaucoup d'informations » du propriétaire.
#
# usage : measure-stream.sh <tag> [secondes] [paquet]
#   ADB     chemin d'adb (défaut : celui du SDK local)
#   SERIAL  appareil ciblé, si plusieurs sont branchés
#
# Le gateways factice (tools/perf/mock-gateway.py) doit tourner sur l'hôte : l'app y pointe
# (http://10.0.2.2:19200, modèle « mock-1 »). On ouvre la conversation la plus récente, on envoie
# une question, et on **ne touche plus à rien** : ce qui est mesuré est le coût du flux seul.
#
# Sortie : $OUT/<tag>-stream-gfxinfo.txt, $OUT/<tag>-stream-framestats.txt, $OUT/<tag>-stream.png
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/android-toolchain/sdk/platform-tools/adb")}
PKG=${3:-com.onyx.app}
TAG=${1:-flux}
SECS=${2:-8}
OUT=${OUT:-$(pwd)/perf-out}
mkdir -p "$OUT"
ADB="$ADB ${SERIAL:+-s $SERIAL}"

$ADB shell am force-stop "$PKG"
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
# La carte « Continuer » de l'accueil.
$ADB shell input tap 540 1098
sleep 5
# Le champ d'écriture ([85,1806][995,1948] sur 1080×2400), puis le clavier se referme : le bouton
# « envoyer » n'est atteignable qu'une fois le clavier rangé.
$ADB shell input tap 540 1877
sleep 2
$ADB shell input text "mesure"
sleep 2
$ADB shell input keyevent 4
sleep 2
$ADB shell input tap 934 2056
sleep 1

$ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
sleep "$SECS"
$ADB shell dumpsys gfxinfo "$PKG" > "$OUT/$TAG-stream-gfxinfo.txt"
$ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$TAG-stream-framestats.txt"
$ADB exec-out screencap -p > "$OUT/$TAG-stream.png"

python3 - "$OUT/$TAG-stream-gfxinfo.txt" "$TAG" "$SECS" <<'PY'
import re, sys
path, tag, secs = sys.argv[1], sys.argv[2], float(sys.argv[3])
text = open(path, encoding="utf-8", errors="replace").read()
def num(label):
    m = re.search(label + r"[^0-9]*([0-9]+)", text)
    return int(m.group(1)) if m else None
total, janky = num("Total frames rendered"), num("Janky frames:")
if total:
    share = f"{100 * janky / total:.1f} %" if janky is not None else "?"
    print(f"{tag} (flux seul, {secs:.0f} s) : {total} trames, {total / secs:.1f} trames/s, {janky} saccadées ({share})")
for p in ("50th", "90th", "95th", "99th"):
    m = re.search(p + r" percentile:\s*([0-9]+)ms", text)
    if m:
        print(f"  {p} percentile : {m.group(1)} ms")
for label in ("Number Slow UI thread", "Number Slow issue draw commands", "Number Missed Vsync", "Number Slow bitmap uploads", "Number High input latency"):
    m = re.search(label + r":\s*([0-9]+)", text)
    if m:
        print(f"  {label} : {m.group(1)}")
PY
