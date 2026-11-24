#!/usr/bin/env bash
# Sets — or checks — the next Onyx version number.
#
# **The rule**: a build *never* carries a number lower than the published line.
#
# Defect reported by the owner: the app offered "version 1.3.17 is out"
# right after it had been shipped an APK "1.3.6", "1.3.7", "1.3.8" — numbers
# **already published** by another author, and lower than the actual line (1.3.17). Two
# people each incrementing from their own `gradle.properties` reuse the
# same numbers: the shipped APK is born stale, and the app is right to offer an update.
#
# The number is therefore read **from GitHub**, not from the file:
#
#     scripts/release-version.sh            # sets the next number in gradle.properties
#     scripts/release-version.sh --check    # writes nothing; fails if the number is too low
#
# `versionName` remains *derived* (`1.3.<code - base>`, see app/build.gradle.kts): there is
# only one thing to write, and it cannot diverge from the displayed name.
set -euo pipefail

REPO="${ONYX_REPO:-JemaOS/hermes-onyx}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/gradle.properties"
CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

[ -f "$PROPS" ] || { echo "gradle.properties introuvable : $PROPS" >&2; exit 2; }
BASE="$(sed -n 's/^onyx\.versionBase=\([0-9]\+\).*/\1/p' "$PROPS" | head -1)"
CURRENT="$(sed -n 's/^onyx\.versionCode=\([0-9]\+\).*/\1/p' "$PROPS" | head -1)"
[ -n "$BASE" ] && [ -n "$CURRENT" ] || { echo "onyx.versionBase / onyx.versionCode absents de $PROPS" >&2; exit 2; }

# The published line: the highest patch number among the `v1.3.N` tags.
PUBLISHED="$(curl -sf -m 25 "https://api.github.com/repos/$REPO/releases?per_page=100" | python3 -c '
import json, re, sys
base = int(sys.argv[1])
best = -1
for r in json.load(sys.stdin):
    m = re.match(r"v1\.3\.(\d+)", r.get("tag_name") or "")
    if m:
        n = int(m.group(1))
        best = max(best, n)
print(base + best if best >= 0 else -1)
' "$BASE")" || { echo "GitHub injoignable : impossible de lire la ligne publiée" >&2; exit 3; }
[ "$PUBLISHED" = "-1" ] || [ -n "$PUBLISHED" ] || { echo "aucune release 1.3.x publiée" >&2; exit 3; }

NEXT=$(( PUBLISHED + 1 ))
LATEST_NAME="1.3.$(( PUBLISHED - BASE ))"

if [ "$CURRENT" -ge "$NEXT" ]; then
  echo "versionCode $CURRENT (1.3.$(( CURRENT - BASE ))) — au-dessus de la ligne publiée ($LATEST_NAME). OK."
  exit 0
fi

if [ "$CHECK" = "1" ]; then
  cat >&2 <<EOF
versionCode $CURRENT (1.3.$(( CURRENT - BASE ))) est DÉJÀ PUBLIÉ ou dépassé : la dernière
version sortie est $LATEST_NAME (code $PUBLISHED).

Un build à ce numéro naîtrait périmé — l'app proposerait aussitôt une mise à jour.
Publie la ligne suivante ($NEXT → 1.3.$(( NEXT - BASE ))), ou relance sans --check pour
la poser automatiquement.
EOF
  exit 1
fi

sed -i "s/^onyx\.versionCode=$CURRENT$/onyx.versionCode=$NEXT/" "$PROPS"
grep -n '^onyx\.versionCode' "$PROPS"
echo "posé : $NEXT → 1.3.$(( NEXT - BASE )) (la ligne publiée s'arrêtait à $LATEST_NAME)"
