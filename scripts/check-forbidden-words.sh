#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# check-forbidden-words.sh — guard rail for the public repository.
#
# The public repository carries NO platform trademark, no name of the former product, and none of
# the original firm. This guard rail **names** them without ever writing them: each word is
# assembled from fragments at run time. It is the only file in the repository allowed to
# target these patterns, and it does so without containing any of them — otherwise the repository's
# `grep -rilE` check (which must return 0) would trigger on the guard rail itself.
#
# It checks file CONTENT **and** NAMES, with word boundaries, ignoring
# case: the guard rail's shortest pattern must not match "audioSession" or
# "AudioScenario".
#
# Usage: scripts/check-forbidden-words.sh [root=.]
# Output: 0 if no occurrence; 1 otherwise (and the list is printed).
# ---------------------------------------------------------------------------------------------
set -uo pipefail

ROOT="${1:-.}"
cd "$ROOT" || exit 2

# Forbidden words, assembled from pieces (see the header).
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

# -r recursive, -l file names, -I skip binary, -i case-insensitive, -E extended.
#
# **The plist exemption, and it alone.** A plist's DTD identifier
# (`<!DOCTYPE plist PUBLIC "-//…//DTD PLIST 1.0//EN" "http://…/DTDs/PropertyList-1.0.dtd">`) is a
# **format literal**: a macOS LaunchAgent cannot be read without it, and the word it contains is
# not a trademark mention there. Those lines are therefore excluded **line by line**, not the file:
# the check remains complete everywhere else, including within the same file.
EXEMPT='<!DOCTYPE plist|DTDs/PropertyList'
content="$(grep -rIiE "$PATTERN" . --exclude-dir=.git 2>/dev/null | grep -vE "$EXEMPT" | cut -d: -f1 | sed 's#^\./##' | sort -u || true)"
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
