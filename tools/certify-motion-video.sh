#!/usr/bin/env bash
# Certifie à l'écran que « Réduire les animations » change bien ce que l'app dessine.
#
# Deux enregistrements de la MÊME interaction, sur le même émulateur, à deux réglages système
# près: `animator_duration_scale` à 1, puis à 0 (c'est le réglage qu'Android met à zéro pour
# « Supprimer les animations », celui que lit `ReduceMotion.kt`). Les images extraites des deux
# vidéos se comparent ensuite à l'œil.
#
# Usage: tools/certify-motion-video.sh <serial> <sortie> [interaction]
# interaction: « drawer » (défaut, glissement depuis le bord gauche) ou « tap »
#
# Sortie: <sortie>/motion-scale1.mp4, <sortie>/motion-scale0.mp4, <sortie>/frames-*/, et deux
# planches-contact <sortie>/sheet-scale{1,0}.png.
set -uo pipefail

SERIAL="${1:-emulator-5554}"
OUT="${2:-/home/john/.hermes/profiles/kilo/cache/scratch/motion-cert}"
INTERACTION="${3:-drawer}"

SDK=/home/john/android-toolchain/sdk
ADB="$SDK/platform-tools/adb"
FFMPEG=/home/john/.hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg
APK="$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk"

mkdir -p "$OUT"
say() { printf '\n== %s\n' "$*"; }

say "APK"
ls -l "$APK"
"$ADB" -s "$SERIAL" install -r "$APK" | tail -2

record() {
    local scale="$1" name="$2"
    say "enregistrement à animator_duration_scale=$scale"
    "$ADB" -s "$SERIAL" shell settings put global animator_duration_scale "$scale"
    "$ADB" -s "$SERIAL" shell settings put global transition_animation_scale "$scale"
    "$ADB" -s "$SERIAL" shell settings put global window_animation_scale "$scale"
    "$ADB" -s "$SERIAL" shell am force-stop com.onyx.app
    "$ADB" -s "$SERIAL" shell am start -n com.onyx.app/.MainActivity >/dev/null
    sleep 5
    "$ADB" -s "$SERIAL" shell screenrecord --time-limit 8 --bit-rate 8000000 "/sdcard/$name.mp4" &
    local recorder=$!
    sleep 2
    case "$INTERACTION" in
        drawer)
            # Glissement depuis le bord gauche: c'est le panneau latéral (`SwipeToOpen`, `.smooth(0.32)`).
            "$ADB" -s "$SERIAL" shell input swipe 4 1100 760 1100 180
            sleep 1
            "$ADB" -s "$SERIAL" shell input swipe 760 1100 4 1100 180
            ;;
        tap)
            "$ADB" -s "$SERIAL" shell input tap 540 1800
            sleep 1
            "$ADB" -s "$SERIAL" shell input tap 540 1200
            ;;
    esac
    wait "$recorder"
    sleep 1
    "$ADB" -s "$SERIAL" pull "/sdcard/$name.mp4" "$OUT/$name.mp4" >/dev/null
    "$ADB" -s "$SERIAL" shell rm -f "/sdcard/$name.mp4"
}

record 1 motion-scale1
record 0 motion-scale0

say "extraction des images et planches-contact"
for scale in scale1 scale0; do
    rm -rf "$OUT/frames-$scale"
    mkdir -p "$OUT/frames-$scale"
    # `-fps_mode passthrough`: une trame par trame encodée, aucune n'est jetée ni dupliquée.
    # (ffmpeg 9 a retiré `-vsync`.)
    "$FFMPEG" -v error -i "$OUT/motion-$scale.mp4" -fps_mode passthrough "$OUT/frames-$scale/%03d.png"
    "$FFMPEG" -y -v error -i "$OUT/motion-$scale.mp4" -vf "fps=6,scale=240:-1,tile=4x3" -frames:v 1 "$OUT/sheet-$scale.png"
    printf '%s : %s images extraites\n' "$scale" "$(ls "$OUT/frames-$scale" | wc -l)"
done

say "empreintes des images (pour compter les trames distinctes)"
for scale in scale1 scale0; do
    printf '%s : %s trames distinctes sur %s\n' \
        "$scale" \
        "$(sha256sum "$OUT/frames-$scale"/*.png | awk '{print $1}' | sort -u | wc -l)" \
        "$(ls "$OUT/frames-$scale" | wc -l)"
done

say "durée et cadence des deux vidéos"
for scale in scale1 scale0; do
    "$FFMPEG" -v error -i "$OUT/motion-$scale.mp4" -f null - 2>&1 | tail -1
    "$FFMPEG" -v error -i "$OUT/motion-$scale.mp4" -hide_banner -f null - 2>&1 | tail -1
done

# Remet l'émulateur comme il était: les animations allumées.
"$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 1
"$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 1
"$ADB" -s "$SERIAL" shell settings put global window_animation_scale 1
say "preuves dans $OUT"
