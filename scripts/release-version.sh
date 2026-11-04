#!/usr/bin/env bash
# Pose — ou vérifie — le numéro de la prochaine version d'Onyx.
#
# **La règle** : un build ne porte *jamais* un numéro inférieur à la ligne publiée.
#
# Défaut relevé par le propriétaire : l'app proposait « la version 1.3.17 est sortie »
# juste après qu'on lui eut livré un APK « 1.3.6 », « 1.3.7 », « 1.3.8 » — des numéros
# **déjà publiés** par un autre auteur, et plus bas que la ligne réelle (1.3.17). Deux
# personnes qui incrémentent chacune depuis leur `gradle.properties` réutilisent les
# mêmes numéros : l'APK livré naît périmé, et l'app a raison de proposer une mise à jour.
#
# Le numéro se lit donc **sur GitHub**, pas dans le fichier :
#
#     scripts/release-version.sh            # pose le numéro suivant dans gradle.properties
#     scripts/release-version.sh --check    # n'écrit rien ; échoue si le numéro est trop bas
#
# `versionName` reste *dérivé* (`1.3.<code - base>`, voir app/build.gradle.kts) : il n'y a
# qu'une seule chose à écrire, et elle ne peut pas diverger du nom affiché.
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

# La ligne publiée : le plus grand numéro de patch parmi les tags `v1.3.N`.
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
