#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# release.sh — publie une version d'Onyx sur GitHub, en une commande.
#
# Ce que le script fait, dans l'ordre :
#   1. lit le `versionName`/`versionCode` de l'APK téléphone (source de vérité) ;
#   2. vérifie la signature de chaque paquet (`apksigner verify --print-certs`) et exige
#      UN SEUL signataire, le MÊME certificat sur les trois ;
#   3. calcule le sha256 de chaque paquet et écrit `releases/SHA256SUMS-<version>` ;
#   4. rend les textes publics (README + notes) en remplaçant les jetons `__VERSION__`,
#      `__SIDECAR__`, `__TESTS__`, et écrit `releases/NOTES-<version>.md` ;
#   5. passe le contrôle des mots interdits (marques de plateforme, nom de l'autre app) ;
#   6. crée la release `v<version>` avec les paquets fournis, marquée **Latest** ;
#   7. re-télécharge CHAQUE paquet depuis GitHub et re-vérifie son empreinte (`sha256sum -c`).
#
# Usage :
#   ./release.sh --phone /chemin/Hermes-Onyx-<version>-telephone.apk \
#                [--auto  /chemin/Hermes-Onyx-<version>-auto.apk] \
#                [--wear  /chemin/Hermes-Onyx-<version>-wear.apk] \
#                [--sidecar 1.0.0+546] [--tests 2644] [--repo JemaOS/hermes-onyx] \
#                [--dry-run] [--skip-download-check]
#
# `--dry-run` : tout est fait SAUF le `gh release create` et le re-téléchargement.
#               Les fichiers suivis (README.md, releases/NOTES-*.md) ne sont PAS réécrits :
#               le rendu part dans un répertoire temporaire et il est affiché.
#
# Ne pousse jamais rien en `--dry-run`. En réel, il pousse la release et le tag `v<version>`.
# ---------------------------------------------------------------------------------------------
set -uo pipefail

REPO="JemaOS/hermes-onyx"
BRANCH="main"
PHONE=""; AUTO=""; WEAR=""
VERSION=""; SIDECAR=""; TESTS=""
DRY_RUN=0
SKIP_DL=0

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

say()  { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }
die()  { printf '\n\033[31mERREUR : %s\033[0m\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --phone)  PHONE="${2:-}"; shift 2 ;;
    --auto)   AUTO="${2:-}";  shift 2 ;;
    --wear)   WEAR="${2:-}";  shift 2 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --sidecar) SIDECAR="${2:-}"; shift 2 ;;
    --tests)  TESTS="${2:-}"; shift 2 ;;
    --repo)   REPO="${2:-}";  shift 2 ;;
    --branch) BRANCH="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --skip-download-check) SKIP_DL=1; shift ;;
    -h|--help) sed -n '2,40p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "argument inconnu : $1" ;;
  esac
done

[ -n "$PHONE" ] || die "il faut --phone <apk> (au minimum)."
[ -f "$PHONE" ] || die "APK introuvable : $PHONE"
[ -f "$AUTO" ]  || { [ -z "$AUTO" ] || die "APK introuvable : $AUTO"; }
[ -f "$WEAR" ]  || { [ -z "$WEAR" ] || die "APK introuvable : $WEAR"; }

# --- Outils Android --------------------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/john/android-toolchain/sdk}}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || die "build-tools introuvable sous $SDK"
AAPT2="$BT/aapt2"; APKSIGNER="$BT/apksigner"
[ -x "$AAPT2" ] || die "aapt2 introuvable : $AAPT2"
[ -x "$APKSIGNER" ] || die "apksigner introuvable : $APKSIGNER"
info "build-tools : ${BT%/}"

# --- 1. version, depuis l'APK téléphone ------------------------------------------------------
BAD="$("$AAPT2" dump badging "$PHONE" 2>/dev/null | head -1)"
VN="$(printf '%s' "$BAD" | sed -E "s/.*versionName='([^']+)'.*/\1/")"
VC="$(printf '%s' "$BAD" | sed -E "s/.*versionCode='([0-9]+)'.*/\1/")"
[ -n "$VN" ] && [ "$VN" != "$BAD" ] || die "versionName illisible dans $PHONE"
[ -n "$VERSION" ] || VERSION="$VN"
# --version accepte « 586 » comme « 1.0.0+586 ».
case "$VERSION" in *+*) : ;; *) VERSION="1.0.0+$VERSION" ;; esac
CODE="${VERSION##*+}"
say "Version"
info "APK téléphone        : $VN (versionCode $VC)"
info "version de publication : $VERSION"
[ "$VERSION" = "$VN" ] || info "ATTENTION : --version ($VERSION) diffère de l'APK ($VN) — c'est --version qui est publié."

# --- 2. signature ----------------------------------------------------------------------------
say "Signatures"
CERT=""
check_apk() {  # $1 = chemin, $2 = rôle
  local apk="$1" role="$2" out certs n
  out="$("$APKSIGNER" verify --print-certs "$apk" 2>&1)" || die "$role : signature invalide\n$out"
  certs="$(printf '%s' "$out" | grep -c 'certificate DN')"
  [ "$certs" -eq 1 ] || die "$role : $certs signataires (il en faut exactement 1)"
  cert="$(printf '%s' "$out" | grep -m1 'SHA-256 digest' | sed -E 's/.*digest: *//')"
  info "$role : signé — cert SHA-256 ${cert:0:16}…"
  if [ -z "$CERT" ]; then CERT="$cert"; else [ "$CERT" = "$cert" ] || die "$role : certificat différent des autres paquets"; fi
}
check_apk "$PHONE" "téléphone"
[ -n "$AUTO" ] && check_apk "$AUTO" "auto"
[ -n "$WEAR" ] && check_apk "$WEAR" "wear"
info "un seul certificat sur tous les paquets : ${CERT:0:16}…"

# --- 3. empreintes ---------------------------------------------------------------------------
say "Empreintes — releases/SHA256SUMS-${VERSION}"
SUMS="$ROOT/releases/SHA256SUMS-${VERSION}"
( cd "$(dirname "$PHONE")" && sha256sum "$(basename "$PHONE")" ) > "$TMP/sums"
[ -n "$AUTO" ] && ( cd "$(dirname "$AUTO")" && sha256sum "$(basename "$AUTO")" ) >> "$TMP/sums"
[ -n "$WEAR" ] && ( cd "$(dirname "$WEAR")" && sha256sum "$(basename "$WEAR")" ) >> "$TMP/sums"
sed 's/^/   /' "$TMP/sums"
cp "$TMP/sums" "$SUMS"; info "écrit : $SUMS"

# --- 4. textes publics -----------------------------------------------------------------------
say "Textes publics"
[ -n "$SIDECAR" ] || SIDECAR="$VERSION"
[ -n "$TESTS" ]   || TESTS="__TESTS__"
render() { sed -e "s/__VERSION__/${VERSION}/g" -e "s/__CODE__/${CODE}/g" -e "s/__SIDECAR__/${SIDECAR}/g" -e "s/__TESTS__/${TESTS}/g" "$1"; }

NOTES_SRC="$ROOT/releases/NOTES-__VERSION__.md"
NOTES_OUT="$ROOT/releases/NOTES-${VERSION}.md"
[ -f "$NOTES_SRC" ] || die "notes de version introuvables : $NOTES_SRC"
if [ "$DRY_RUN" = 1 ]; then
  render "$ROOT/README.md" > "$TMP/README.md"
  render "$NOTES_SRC"      > "$TMP/NOTES-${VERSION}.md"
  info "README.md      -> rendu dans $TMP/README.md (non écrit)"
  info "notes          -> rendues dans $TMP/NOTES-${VERSION}.md (non écrites)"
else
  render "$ROOT/README.md" > "$TMP/README.md" && mv "$TMP/README.md" "$ROOT/README.md"
  cp "$NOTES_SRC" "$TMP/NOTES-${VERSION}.md"
  render "$NOTES_SRC" > "$NOTES_OUT"
  info "README.md      -> réécrit (jetons remplacés)"
  info "notes          -> $NOTES_OUT"
fi

# --- 5. mots interdits -----------------------------------------------------------------------
say "Contrôle des mots interdits"
FORBIDDEN_SCRIPT="$ROOT/scripts/check-forbidden-words.sh"
if [ -x "$FORBIDDEN_SCRIPT" ]; then
  bash "$FORBIDDEN_SCRIPT" "$ROOT" || die "mots interdits trouvés — corriger avant de publier."
else
  info "(scripts/check-forbidden-words.sh absent — contrôle sauté)"
fi

# --- 6. release GitHub -----------------------------------------------------------------------
TAG="v${VERSION}"
ASSETS=("$PHONE"); [ -n "$AUTO" ] && ASSETS+=("$AUTO"); [ -n "$WEAR" ] && ASSETS+=("$WEAR")
# Le fichier d'empreintes est publié avec les paquets (l'asset porte le nom « SHA256SUMS-<version> »).
ASSETS+=("$SUMS")
say "Release GitHub — $TAG (Latest) sur $REPO"
CMD=(gh release create "$TAG" --repo "$REPO" --target "$BRANCH"
     --title "Hermes Onyx ${VERSION}" --notes-file "$NOTES_OUT" --latest "${ASSETS[@]}")
printf '   %q ' "${CMD[@]}"; printf '\n'
if [ "$DRY_RUN" = 1 ]; then
  cat <<EOF

   --- NOTES qui seraient publiées (dry-run) ---
$(sed 's/^/   /' "$TMP/NOTES-${VERSION}.md")
EOF
  info "(dry-run : aucune release n'est créée, rien n'est poussé)"
else
  gh auth status >/dev/null 2>&1 || die "gh n'est pas authentifié."
  "${CMD[@]}" || die "gh release create a échoué"
  info "release créée. Noms d'assets lus depuis GitHub :"
  gh api "repos/$REPO/releases/tags/$TAG" --jq '.assets[].name' | sed 's/^/   /'
fi

# --- 7. re-téléchargement + re-vérification --------------------------------------------------
if [ "$DRY_RUN" = 1 ] || [ "$SKIP_DL" = 1 ]; then
  say "Re-téléchargement"
  info "(sauté en dry-run / --skip-download-check)"
else
  say "Re-téléchargement et re-vérification des empreintes"
  mkdir -p "$TMP/dl"
  gh release download "$TAG" --repo "$REPO" --dir "$TMP/dl" --clobber || die "gh release download a échoué"
  cp "$SUMS" "$TMP/dl/SHA256SUMS"
  ( cd "$TMP/dl" && sha256sum -c SHA256SUMS ) || die "les empreintes re-téléchargées NE CORRESPONDENT PAS"
  info "les trois paquets re-téléchargés portent bien les empreintes publiées."
fi

say "Terminé"
info "release : https://github.com/$REPO/releases/tag/$TAG"
