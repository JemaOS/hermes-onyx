#!/usr/bin/env bash
# Comparaison A/B d'un **long défilement** à travers un fil dense.
#
# Ce que la mesure d'attribution a montré : le défaut (la matière reconstruite par image) ne se
# voit pas sur un geste court — il faut **traverser** le contenu dense, parce que c'est le
# défilement qui fait travailler le fond de la page à chaque image.
#
# Le geste part de **y=700** (dans la zone de la liste) et va vers **y=1900** : à 1900 on est sur
# la plaque du compositeur, qui capte le doigt — la liste ne défile pas et la passe ne compte que
# des trames vides. La conversation s'ouvre **en bas** : on remonte donc le fil, traversant les
# rangées denses, ce qui est exactement la plainte.
#
# Une **trame par passe** : `framestats` ne garde que les 120 dernières trames, et un long
# défilement en produit des centaines. Le compteur est donc vidé avant **chaque** geste, et les
# passes sont agrégées ensuite.
#
# usage : ab-long.sh <apkA> <apkB> [tag] [passes]
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

capture () {  # $1 = apk, $2 = etiquette, $3 = numero
  local apk=$1 label=$2 n=$3
  $ADB install -r -d "$apk" >/dev/null 2>&1 || { echo "  [échec] install $label" >&2; return 1; }
  $ADB shell am force-stop "$PKG" >/dev/null 2>&1
  $ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 10
  $ADB shell input tap 540 1098 >/dev/null 2>&1
  sleep 8
  alive || return 1
  # N glissements qui remontent le fil : la conversation dense est traversee.
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
