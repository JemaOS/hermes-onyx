#!/usr/bin/env bash
# Measure the scrolling of a dense thread **on the device** (the Snapdragon 8 Elite 3).
#
# It is the only measurement that settles real smoothness: the emulator renders in translated GL, and its
# jank percentage cannot be transposed. What can be, on the device: the **UI thread per
# frame** (it does not depend on the GPU) and the **budget** — 8.33 ms at 120 Hz, 16.67 ms at 60 Hz.
#
# usage:
#   export ADB=$(command -v adb) SERIAL=<your-device>
#   sh tools/perf/measure-on-phone.sh <tag> [swipes] [package]
#
# Output: $OUT/<tag>-framestats.txt, $OUT/<tag>.png, and the summary on standard output.
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/android-toolchain/sdk/platform-tools/adb")}
PKG=${3:-com.onyx.app}
TAG=${1:-telephone}
N=${2:-8}
OUT=${OUT:-$(pwd)/perf-out}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
ADB="$ADB ${SERIAL:+-s $SERIAL}"

# A dense thread in the app's data. On an **unrooted** device, the copy is not done
# this way: open the conversation from the app, then paste the text into it. The rest of the protocol is
# identical.
if [ -f /tmp/onyx-dense/conversations.json ]; then
  $ADB push /tmp/onyx-dense/conversations.json /data/local/tmp/c.json >/dev/null 2>&1
  $ADB shell "su 0 cp /data/local/tmp/c.json /data/data/$PKG/files/conversations.json" >/dev/null 2>&1
fi

$ADB shell am force-stop "$PKG"
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
# The "Continue" card on the home screen. On another screen, you need the already open thread.
$ADB shell input tap 540 1098
sleep 8

# The gesture starts at **y=700** (inside the list area) and goes to **y=1900**: at 1900 you are on
# the composer panel, which captures the finger — the list does not scroll, and the pass counts only
# empty frames. The conversation opens **at the bottom**: so we scroll the thread upward, through the
# dense content — this is the gesture from the complaint.
#
# One **frame per pass**: `framestats` keeps only the last 120 frames, and a long scroll
# produces hundreds. The counter is cleared before each gesture.
for i in $(seq 1 "$N"); do
  $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
  $ADB shell input swipe 540 700 540 1900 700
  sleep 0.8
  $ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$TAG-geste$(printf '%02d' "$i").txt"
done

$ADB exec-out screencap -p > "$OUT/$TAG.png"

python3 "$HERE/aggregate-frames.py" "$OUT/$TAG-frames.txt" "$OUT/$TAG-geste*.txt"

# The screen refresh rate: the budget depends on it.
echo "--- ecran ---"
$ADB shell dumpsys display 2>/dev/null | grep -oE "renderFrameRate [0-9.]+" | head -1
