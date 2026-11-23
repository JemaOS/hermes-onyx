#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# release.sh — publishes a version of Onyx to GitHub in a single command.
#
# What the script does, in order:
#   1. reads `versionName`/`versionCode` from the phone APK (source of truth);
#   2. verifies the signature of each package (`apksigner verify --print-certs`) and requires
#      a SINGLE signer, the SAME certificate on all three;
#   3. computes the sha256 of each package (asset `SHA256SUMS-<version>.txt`);
#   4. renders the public texts (README + notes) by replacing the `__VERSION__`,
#      `__SIDECAR__`, `__TESTS__` tokens, and generates the release notes;
#   5. runs the forbidden-words check (platform trademarks, the other app's name);
#   6. creates the `v<version>` release with the supplied packages, flagged **Latest**;
#   7. re-downloads EVERY package from GitHub and re-verifies its digest (`sha256sum -c`).
#
# Usage:
#   ./release.sh --phone /path/Hermes-Onyx-<version>-telephone.apk \
#                [--auto  /path/Hermes-Onyx-<version>-auto.apk] \
#                [--wear  /path/Hermes-Onyx-<version>-wear.apk] \
#                [--sidecar 1.0.0+546] [--tests 2644] [--repo JemaOS/hermes-onyx] \
#                [--dry-run] [--skip-download-check]
#
# `--dry-run`: everything is done EXCEPT `gh release create` and the re-download.
#               Tracked files (README.md) are NOT rewritten:
#               the rendering goes to a temporary directory and is printed.
#
# Never pushes anything in `--dry-run`. In real mode, it pushes the release and the `v<version>` tag.
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

# --- Android tools ---------------------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/john/android-toolchain/sdk}}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || die "build-tools introuvable sous $SDK"
AAPT2="$BT/aapt2"; APKSIGNER="$BT/apksigner"
[ -x "$AAPT2" ] || die "aapt2 introuvable : $AAPT2"
[ -x "$APKSIGNER" ] || die "apksigner introuvable : $APKSIGNER"
info "build-tools : ${BT%/}"

# --- 1. version, from the phone APK ----------------------------------------------------------
BAD="$("$AAPT2" dump badging "$PHONE" 2>/dev/null | head -1)"
VN="$(printf '%s' "$BAD" | sed -E "s/.*versionName='([^']+)'.*/\1/")"
VC="$(printf '%s' "$BAD" | sed -E "s/.*versionCode='([0-9]+)'.*/\1/")"
[ -n "$VN" ] && [ "$VN" != "$BAD" ] || die "versionName illisible dans $PHONE"
[ -n "$VERSION" ] || VERSION="$VN"
# --version accepts "586" as well as "1.0.0+586", and lets an already-formed `versionName`
# ("1.3.96") through: since 1.3.x the published number is no longer a bare code, and prefixing
# it produced a `v1.0.0+1.3.96` tag that does not exist.
case "$VERSION" in
  *+*) : ;;                        # already complete
  ''|*[!0-9]*) : ;;                # 1.3.96: versionName as is
  *) VERSION="1.0.0+$VERSION" ;;   # "586": old style
esac
CODE="${VERSION##*+}"
say "Version"
info "APK téléphone        : $VN (versionCode $VC)"
info "version de publication : $VERSION"
[ "$VERSION" = "$VN" ] || info "ATTENTION : --version ($VERSION) diffère de l'APK ($VN) — c'est --version qui est publié."

# --- 2. signature ----------------------------------------------------------------------------
say "Signatures"
CERT=""
check_apk() {  # $1 = path, $2 = role
  local apk="$1" role="$2" out certs n
  out="$("$APKSIGNER" verify --print-certs "$apk" 2>&1)" || die "$role : signature invalide\n$out"
  certs="$(printf '%s' "$out" | grep -c 'certificate DN')"
  [ "$certs" -eq 1 ] || die "$role : $certs signataires (il en faut exactement 1)"
  # A package signed with the debug key would pass the check above (a single
  # signer, the same everywhere) while being **impossible to update** anywhere other than
  # the machine that signed it. That happened up to 1.3.96: we now refuse it explicitly.
  dn="$(printf '%s' "$out" | grep -m1 'certificate DN' | sed -E 's/.*certificate DN: *//')"
  case "$dn" in
    *"CN=Android Debug"*) die "$role : signé par la clé de DÉBOGAGE ($dn).\n   Signe avec la clé de release du projet avant de publier." ;;
  esac
  info "$role : certificat — $dn"
  cert="$(printf '%s' "$out" | grep -m1 'SHA-256 digest' | sed -E 's/.*digest: *//')"
  info "$role : signé — cert SHA-256 ${cert:0:16}…"
  if [ -z "$CERT" ]; then CERT="$cert"; else [ "$CERT" = "$cert" ] || die "$role : certificat différent des autres paquets"; fi
}
check_apk "$PHONE" "téléphone"
[ -n "$AUTO" ] && check_apk "$AUTO" "auto"
[ -n "$WEAR" ] && check_apk "$WEAR" "wear"
info "un seul certificat sur tous les paquets : ${CERT:0:16}…"

# --- 3. digests ------------------------------------------------------------------------------
say "Empreintes — SHA256SUMS-${VERSION}.txt"
SUMS="$TMP/SHA256SUMS-${VERSION}.txt"
( cd "$(dirname "$PHONE")" && sha256sum "$(basename "$PHONE")" ) > "$TMP/sums"
[ -n "$AUTO" ] && ( cd "$(dirname "$AUTO")" && sha256sum "$(basename "$AUTO")" ) >> "$TMP/sums"
[ -n "$WEAR" ] && ( cd "$(dirname "$WEAR")" && sha256sum "$(basename "$WEAR")" ) >> "$TMP/sums"
sed 's/^/   /' "$TMP/sums"
cp "$TMP/sums" "$SUMS"; info "écrit (temporaire, publié en asset) : $SUMS"

# --- 4. public texts -------------------------------------------------------------------------
say "Textes publics"
[ -n "$SIDECAR" ] || SIDECAR="$VERSION"
[ -n "$TESTS" ]   || TESTS="__TESTS__"
render() { sed -e "s/__VERSION__/${VERSION}/g" -e "s/__CODE__/${CODE}/g" -e "s/__SIDECAR__/${SIDECAR}/g" -e "s/__TESTS__/${TESTS}/g" "$1"; }

NOTES_SRC="$ROOT/releases/NOTES-__VERSION__.md"
NOTES_OUT="$TMP/NOTES-${VERSION}.md"
if [ -f "$NOTES_SRC" ]; then
  render "$NOTES_SRC" > "$NOTES_OUT"
else
  # No template: minimal notes, in the shape of previous releases.
  TCOUNT="$TESTS"; [ "$TCOUNT" = "__TESTS__" ] && TCOUNT="count not reported"
  {
    printf '# Release notes — Hermes Onyx `%s`\n\n' "$VERSION"
    printf '**Version:** `%s` (`versionCode` = `%s`).\n' "$VERSION" "$CODE"
    printf '**Packages:** `onyx-app-%s-release.apk`, `onyx-auto-%s-release.apk`, `onyx-wear-%s-release.apk`; checksums in `SHA256SUMS-%s.txt`.\n' "$VERSION" "$VERSION" "$VERSION" "$VERSION"
    printf '**Test suite:** %s tests.\n\n---\n\n## What'"'"'s Changed\n\n' "$TCOUNT"
    PREV="$(git tag --merged HEAD --sort=-v:refname | head -1)"
    if [ -n "$PREV" ]; then
      git log --no-merges --pretty='- %s (%h)' "$PREV..HEAD"
    else
      git log --no-merges --pretty='- %s (%h)' -20
    fi
  } > "$NOTES_OUT"
fi
if [ "$DRY_RUN" = 1 ]; then
  render "$ROOT/README.md" > "$TMP/README.md"
  info "README.md      -> rendu dans $TMP/README.md (non écrit)"
  info "notes          -> rendues dans $NOTES_OUT (non écrites)"
else
  render "$ROOT/README.md" > "$TMP/README.md" && mv "$TMP/README.md" "$ROOT/README.md"
  info "README.md      -> réécrit (jetons remplacés)"
  info "notes          -> $NOTES_OUT"
fi

# --- 5. forbidden words ----------------------------------------------------------------------
say "Contrôle des mots interdits"
FORBIDDEN_SCRIPT="$ROOT/scripts/check-forbidden-words.sh"
if [ -x "$FORBIDDEN_SCRIPT" ]; then
  bash "$FORBIDDEN_SCRIPT" "$ROOT" || die "mots interdits trouvés — corriger avant de publier."
else
  info "(scripts/check-forbidden-words.sh absent — contrôle sauté)"
fi

# --- 6. GitHub release -----------------------------------------------------------------------
TAG="v${VERSION}"
ASSETS=("$PHONE"); [ -n "$AUTO" ] && ASSETS+=("$AUTO"); [ -n "$WEAR" ] && ASSETS+=("$WEAR")
# The checksums file is published with the packages (the asset is named "SHA256SUMS-<version>").
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

# --- 7. re-download + re-verification --------------------------------------------------------
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
