#!/usr/bin/env bash
# Measurement of a response being written: the owner's "lots of information" scene.
#
# usage: measure-stream.sh <tag> [seconds] [package]
#   ADB     path to adb (default: the local SDK one)
#   SERIAL  targeted device, if several are connected
#
# The mock gateway (tools/perf/mock-gateway.py) must run on the host: the app points to it
# (http://10.0.2.2:19200, model "mock-1"). We open the most recent conversation, send
# a question, and **do not touch anything else**: what is measured is the cost of the stream alone.
#
# Output: $OUT/<tag>-stream-gfxinfo.txt, $OUT/<tag>-stream-framestats.txt, $OUT/<tag>-stream.png
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
# The "Continue" card on the home screen.
$ADB shell input tap 540 1098
sleep 5
# The writing field ([85,1806][995,1948] on 1080×2400), then the keyboard closes: the
# "send" button is only reachable once the keyboard is put away.
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
