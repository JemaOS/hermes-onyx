#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# check-forbidden-words.sh — garde-fou du dépôt public.
#
# Le dépôt public ne porte AUCUNE marque de plateforme, aucun nom de l'ancien produit ni celui de
# la firme d'origine. Ce garde-fou les **désigne** sans jamais les écrire : chaque mot est
# assemblé par fragments au moment de l'exécution. C'est le seul fichier du dépôt autorisé à
# viser ces motifs, et il le fait sans en contenir aucun — sans quoi le contrôle `grep -rilE` du
# dépôt (qui doit rendre 0) se déclencherait sur le garde-fou lui-même.
#
# Il vérifie le CONTENU **et** les NOMS de fichiers, à bornes de mot, sans tenir compte de la
# casse : le motif le plus court du garde-fou ne doit pas matcher « audioSession » ni
# « AudioScenario ».
#
# Usage : scripts/check-forbidden-words.sh [racine=.]
# Sortie : 0 si aucune occurrence ; 1 sinon (et la liste est imprimée).
# ---------------------------------------------------------------------------------------------
set -uo pipefail

ROOT="${1:-.}"
cd "$ROOT" || exit 2

# Mots interdits, assemblés par morceaux (voir l'entête).
W_OLD_PRODUCT="red""de"
W_FIRM="goose""house"
W1="i""phone"
W2="i""pad"
W3="ap""ple"
W4="i""os"
W5="sw""ift"
W6="x""code"
W7="si""ri"

PATTERN="\\b(${W_OLD_PRODUCT}|${W_FIRM}|${W1}|${W2}|${W3}|${W4}|${W5}|${W6}|${W7})\\b"

hits=0
printf 'Dépôt scanné : %s\n' "$(pwd)"

# -r récursif, -l noms de fichiers, -I saute le binaire, -i insensible à la casse, -E étendu.
content="$(grep -rlIiE "$PATTERN" . --exclude-dir=.git 2>/dev/null | sed 's#^\./##' | sort || true)"
if [ -n "$content" ]; then
  printf '\n   [CONTENU] %s fichier(s) :\n' "$(printf '%s\n' "$content" | grep -c .)"
  printf '%s\n' "$content" | sed 's/^/      /'
  hits=$(( hits + $(printf '%s\n' "$content" | grep -c .) ))
fi

names="$(find . -path ./.git -prune -o -print | grep -IiE "$PATTERN" | sed 's#^\./##' | sort || true)"
if [ -n "$names" ]; then
  printf '\n   [NOMS] %s chemin(s) :\n' "$(printf '%s\n' "$names" | grep -c .)"
  printf '%s\n' "$names" | sed 's/^/      /'
  hits=$(( hits + $(printf '%s\n' "$names" | grep -c .) ))
fi

if [ "$hits" -gt 0 ]; then
  printf '\n\033[31m%s occurrence(s) — à corriger avant de publier.\033[0m\n' "$hits"
  exit 1
fi
printf '\033[32mAucune occurrence : le contenu et les noms de fichiers sont propres.\033[0m\n'
exit 0
