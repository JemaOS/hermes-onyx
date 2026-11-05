#!/usr/bin/env bash
# Mesurer le défilement d'un fil dense **sur l'appareil** (le Snapdragon 8 Elite 3).
#
# C'est la seule mesure qui tranche la fluidité réelle : l'emulateur rend en GL traduit, et son
# pourcentage de saccades n'est pas transposable. Ce qui l'est, sur l'appareil : le **thread UI par
# image** (il ne depend pas du GPU) et le **budget** — 8,33 ms a 120 Hz, 16,67 ms a 60 Hz.
#
# usage :
#   export ADB=$(command -v adb) SERIAL=<ton-appareil>
#   sh tools/perf/measure-on-phone.sh <tag> [glissements] [paquet]
#
# Sortie : $OUT/<tag>-framestats.txt, $OUT/<tag>.png, et le resume sur la sortie standard.
set -u

ADB=${ADB:-$(command -v adb || echo "$HOME/android-toolchain/sdk/platform-tools/adb")}
PKG=${3:-com.onyx.app}
TAG=${1:-telephone}
N=${2:-8}
OUT=${OUT:-$(pwd)/perf-out}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
ADB="$ADB ${SERIAL:+-s $SERIAL}"

# Un fil dense dans les donnees de l'app. Sur un appareil **non roote**, la copie ne se fait pas
# ainsi : ouvrir la conversation depuis l'app, puis y coller le texte. Le reste du protocole est
# identique.
if [ -f /tmp/onyx-dense/conversations.json ]; then
  $ADB push /tmp/onyx-dense/conversations.json /data/local/tmp/c.json >/dev/null 2>&1
  $ADB shell "su 0 cp /data/local/tmp/c.json /data/data/$PKG/files/conversations.json" >/dev/null 2>&1
fi

$ADB shell am force-stop "$PKG"
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
# La carte « Continuer » de l'accueil. Sur un autre ecran, c'est le fil deja ouvert qu'il faut.
$ADB shell input tap 540 1098
sleep 8

# Le geste part de **y=700** (dans la zone de la liste) et va vers **y=1900** : a 1900 on est sur
# la plaque du compositeur, qui capte le doigt — la liste ne defile pas, et la passe ne compte que
# des trames vides. La conversation s'ouvre **en bas** : on remonte donc le fil, a travers le
# contenu dense — c'est le geste de la plainte.
#
# Une **trame par passe** : `framestats` ne garde que les 120 dernieres trames, et un defilement
# long en produit des centaines. Le compteur est vide avant chaque geste.
for i in $(seq 1 "$N"); do
  $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
  $ADB shell input swipe 540 700 540 1900 700
  sleep 0.8
  $ADB shell dumpsys gfxinfo "$PKG" framestats > "$OUT/$TAG-geste$(printf '%02d' "$i").txt"
done

$ADB exec-out screencap -p > "$OUT/$TAG.png"

python3 "$HERE/aggregate-frames.py" "$OUT/$TAG-frames.txt" "$OUT/$TAG-geste*.txt"

# Le taux de rafraichissement de l'ecran : le budget depend de lui.
echo "--- ecran ---"
$ADB shell dumpsys display 2>/dev/null | grep -oE "renderFrameRate [0-9.]+" | head -1
