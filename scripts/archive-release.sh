#!/usr/bin/env bash
# Archive les APK release de Onyx: un fichier versionné + son SHA-256 + une ligne de CHANGELOG.
# Appelé après chaque assembleRelease. Ne réécrit jamais une archive existante.
set -uo pipefail

ROOT=/home/john/r132/onyx-android
BT=/home/john/android-toolchain/sdk/build-tools/36.0.0
OUT="$ROOT/releases"
STAMP=$(date +%Y%m%d-%H%M)
mkdir -p "$OUT"

if [ ! -f "$OUT/CHANGELOG.md" ]; then
  cat > "$OUT/CHANGELOG.md" <<'EOF'
# CHANGELOG des APK Onyx

Une ligne par build livré. `versionCode` est le nombre de commits au moment du build
(il croît donc toujours : condition pour qu'un APK s'installe par-dessus le précédent).
`versionName` = `1.0.0+<versionCode>`.

| date | fichier | module | versionName | versionCode | taille | sha256 |
|---|---|---|---|---|---|---|
EOF
fi

for m in app wear auto; do
  APK=$(ls -t "$ROOT/$m/build/outputs/apk/release/"*.apk 2>/dev/null | head -1)
  [ -n "$APK" ] || { echo "($m : pas d'APK release)"; continue; }
  BAD=$("$BT/aapt2" dump badging "$APK" 2>/dev/null | head -1)
  VC=$(echo "$BAD" | sed -E "s/.*versionCode='([0-9]+)'.*/\1/")
  VN=$(echo "$BAD" | sed -E "s/.*versionName='([^']+)'.*/\1/")
  SAFE=$(echo "$VN" | tr '+/' '__')
  BASE="$m-v${SAFE}-${STAMP}"
  [ -f "$OUT/$BASE.apk" ] && BASE="${BASE}_2"
  cp -f "$APK" "$OUT/$BASE.apk"
  ( cd "$OUT" && sha256sum "$BASE.apk" > "$BASE.apk.sha256" )
  SIZE=$(stat -c %s "$OUT/$BASE.apk")
  SHA=$(cut -d' ' -f1 "$OUT/$BASE.apk.sha256")
  MB=$(( SIZE / 1048576 ))
  echo "| $(date '+%Y-%m-%d %H:%M') | \`$BASE.apk\` | $m | $VN | $VC | ${MB} Mo | \`${SHA:0:12}\` |" >> "$OUT/CHANGELOG.md"
  echo "archivé : $BASE.apk — $VN (code $VC) — ${MB} Mo"
done

echo
echo "=== archives présentes ==="
ls -1t "$OUT"/*.apk 2>/dev/null | head -8
