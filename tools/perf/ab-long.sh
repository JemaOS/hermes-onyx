#!/usr/bin/env bash
# A/B comparison of a **long scroll** through a dense thread.
#
# What the attribution measurement showed: the defect (content rebuilt per frame) does not
# show up on a short gesture — you have to **traverse** the dense content, because it is the
# scrolling that makes the page background work on every frame.
#
# The gesture starts at **y=700** (inside the list area) and goes to **y=1900**: at 1900 you are on
# the composer panel, which captures the finger — the list does not scroll and the pass counts only
# empty frames. The conversation opens **at the bottom**: so we scroll the thread upward, traversing
# the dense rows, which is exactly the complaint.
#
# One **frame per pass**: `framestats` keeps only the last 120 frames, and a long
# scroll produces hundreds. The counter is therefore cleared before **every** gesture, and the
# passes are aggregated afterwards.
#
# usage: ab-long.sh <apkA> <apkB> [tag] [passes]
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/android-toolchain/sdk/platform-tools/adb")}
PKG=com.onyx.app
APK_A=$1
APK_B=$2
TAG=${3:-long}
N=${4:-6}
OUT=${OUT:-/tmp/onyx-ab-long}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
rm -f "$OUT"/*.txt "$OUT"/*.png
ADB="$ADB ${SERIAL:+-s $SERIAL}"

alive () { $ADB devices 2>/dev/null | grep -q "device$"; }

$ADB push /tmp/onyx-dense/conversations.json /data/local/tmp/c.json >/dev/null 2>&1
$ADB shell "su 0 cp /data/local/tmp/c.json /data/data/$PKG/files/conversations.json" >/dev/null 2>&1

capture () {  # $1 = apk, $2 = label, $3 = number
  local apk=$1 label=$2 n=$3
  $ADB install -r -d "$apk" >/dev/null 2>&1 || { echo "  [échec] install $label" >&2; return 1; }
  $ADB shell am force-stop "$PKG" >/dev/null 2>&1
  $ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 10
  $ADB shell input tap 540 1098 >/dev/null 2>&1
  sleep 8
  alive || return 1
  # N swipes that scroll the thread up: the dense conversation is traversed.
  local i
  for i in 1 2 3 4 5 6; do
    $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
    $ADB shell input swipe 540 700 540 1900 700 >/dev/null 2>&1
    sleep 0.8
    alive || return 1
    $ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$label-p$(printf '%02d' "$n")-$(printf '%02d' "$i").txt" 2>/dev/null
  done
  $ADB exec-out screencap -p > "$OUT/$label-p$(printf '%02d' "$n").png" 2>/dev/null
  return 0
}

for pass in $(seq 1 "$N"); do
  for side in A B; do
    if [ "$side" = "A" ]; then apk=$APK_A; label=a; else apk=$APK_B; label=b; fi
    capture "$apk" "$label" "$pass" || echo "  [passe perdue] $label passe $pass" >&2
  done
done

echo "===== A (base) ====="
python3 "$HERE/aggregate-frames.py" "" "$OUT"/a-p*.txt
echo "===== B (variante) ====="
python3 "$HERE/aggregate-frames.py" "" "$OUT"/b-p*.txt
