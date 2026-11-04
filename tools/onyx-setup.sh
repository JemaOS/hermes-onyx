#!/usr/bin/env bash
# Onyx — premier démarrage, en une commande.
#
# Ce que fait ce script, dans cet ordre, et rien d'autre :
#
#   1. vérifie ce qui doit exister (Hermes qui tourne, cloudflared présent) ;
#   2. tire une clé unique et les secrets locaux, en 0600 ;
#   3. installe et active les trois services (dashboard, passerelle, tunnels) ;
#   4. attend que les tunnels publient leur adresse ;
#   5. écrit le lien `onyx://connect`, l'affiche, et en donne un QR code.
#
# Il est **idempotent** : le relancer ne casse rien, ne change pas la clé, et se contente de
# redonner le lien courant. C'est ce qu'un nouvel utilisateur doit pouvoir faire sans lire une
# page de documentation.
#
# Usage :  onyx-setup.sh            installe / répare, puis donne le lien
#          onyx-setup.sh --link     redonne seulement le lien courant (et son QR)

set -euo pipefail

RACINE="${HOME}/.onyx-one"
AFFICHAGE="${DISPLAY:-:0}"
CLE_FICHIER="${RACINE}/cle"
JETON_FICHIER="${RACINE}/jeton-dashboard"
MDP_FICHIER="${RACINE}/mot-de-passe-dashboard"
ENV_DASH="${RACINE}/dashboard.env"
LIEN_FICHIER="${HOME}/onyx-connect-link.txt"
QR_PNG="${HOME}/onyx-connect-qr.png"
UNITES="${HOME}/.config/systemd/user"
VENV="${RACINE}/venv"
PORT_PASSERELLE=8890
PORT_DASHBOARD=9119
PORT_API=8642

die() { printf '\n  %s\n\n' "$*" >&2; exit 1; }
etape() { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
attention() { printf '  \033[33m!\033[0m %s\n' "$*"; }

mkdir -p "$RACINE"

# **Le script porte sa propre passerelle.** Lancé depuis un clone du dépôt, il installe le
# `onyx-one.py` qui se trouve à côté de lui ; relancé depuis `~/.onyx-one`, il trouve le sien.
# Sans cela, une mise à jour du dépôt ne changerait rien à ce qui tourne.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${SCRIPT_DIR}/onyx-one.py" ] && [ "${SCRIPT_DIR}/onyx-one.py" != "${RACINE}/onyx-one.py" ]; then
  if ! cmp -s "${SCRIPT_DIR}/onyx-one.py" "${RACINE}/onyx-one.py"; then
    cp "${SCRIPT_DIR}/onyx-one.py" "${RACINE}/onyx-one.py"
    REINSTALLER_PASSERELLE=1
  fi
fi
REINSTALLER_PASSERELLE="${REINSTALLER_PASSERELLE:-0}"

# --- 0. --link : juste redonner le lien courant -------------------------------------------

donner_lien() {
  local serveur voix lien
  local log
  log="$(systemctl --user show onyx-one-tunnel.service -p ExecStart 2>/dev/null | grep -oE -- '--logfile [^ ;"]+' | awk '{print $2}' | head -1)"
  serveur="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "${log:-${RACINE}/tunnel.log}" 2>/dev/null | tail -1 || true)"
  [ -n "$serveur" ] || die "Aucun tunnel de passerelle n'est publié. Relance : $0"
  # Les voix : l'adresse qu'on a enregistrée (`--voices`), sinon le tunnel d'un Kokoro local,
  # sinon rien — un lien sans voix reste parfaitement utilisable.
  voix="$(cat "${RACINE}/voix" 2>/dev/null || true)"
  if [ -z "$voix" ]; then
    voix="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "${HOME}/kokoro-tts/tunnel.log" 2>/dev/null | tail -1 || true)"
  fi
  pont="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "${HOME}/.onyx-computer-bridge/tunnel.log" 2>/dev/null | tail -1 || true)"
  pont_jeton="$(cat "${HOME}/.onyx-computer-bridge/token" 2>/dev/null || true)"

  lien="$(python3 - "$serveur" "$voix" "$pont" "$pont_jeton" <<'PY'
import sys, urllib.parse
serveur, voix, PONT = sys.argv[1], sys.argv[2], sys.argv[3]
champs = [("v", "1"), ("server", serveur), ("key", open(f"{__import__('os').path.expanduser('~')}/.onyx-one/cle").read().strip()),
          ("model", "hermes-agent")]
if voix:
    champs.append(("voices", voix))
JETON_PONT = sys.argv[4] if len(sys.argv) > 4 else ""
if PONT:
    champs.append(("bridge", PONT))
if JETON_PONT:
    champs.append(("bridge-token", JETON_PONT))
champs.append(("lang", "fr"))
print("onyx://connect?" + urllib.parse.urlencode(champs))
PY
)"
  printf '%s\n' "$lien" > "$LIEN_FICHIER"
  chmod 600 "$LIEN_FICHIER"

  etape "Le lien"
  printf '\n%s\n\n' "$lien"
  printf '  écrit dans %s\n' "$LIEN_FICHIER"

  # Dernier contrôle, sur **cette** adresse : le nom a pu changer entre-temps.
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' -m 10 "${serveur}/health" 2>/dev/null || true)"
  if [ "$code" = "200" ]; then
    ok "le tunnel répond"
  else
    attention "le tunnel ne répond pas encore (${code:-injoignable}) — le lien sera valable dans quelques secondes"
  fi

  if "$VENV/bin/python" -c 'import segno' 2>/dev/null; then
    "$VENV/bin/python" - "$lien" "$QR_PNG" <<'PY'
import sys, segno
lien, png = sys.argv[1], sys.argv[2]
code = segno.make(lien, error='m')
code.save(png, scale=8, border=2)
print(f"  QR enregistré : {png}")
PY
    # Le QR dans le terminal : deux caractères par module, pour rester carré.
    "$VENV/bin/python" - "$lien" <<'PY'
import sys, segno
qr = segno.make(sys.argv[1], error='m')
mat = qr.matrix
marge = 2
blanc = "  "
noir = "\033[40m  \033[0m"
for _ in range(marge):
    print("   " + " " * (len(mat[0]) + 2 * marge) * 2)
for ligne in mat:
    print("   " + " " * (marge * 2) + "".join(noir if c else blanc for c in ligne) + " " * (marge * 2))
for _ in range(marge):
    print("   " + " " * (len(mat[0]) + 2 * marge) * 2)
PY
  else
    attention "segno absent : pas de QR (relance sans --link pour l'installer)"
  fi
}

# Options : --link (redonner le lien), --voices <url> (un serveur TTS Kokoro-compatible).
MODE="install"
while [ $# -gt 0 ]; do
  case "$1" in
    --link) MODE="link"; shift ;;
    --voices)
      [ -n "${2:-}" ] || die "--voices attend une adresse (https://…)"
      printf '%s\n' "$2" > "${RACINE}/voix"
      chmod 600 "${RACINE}/voix"
      echo "  adresse des voix enregistrée : $2"
      shift 2 ;;
    *) die "option inconnue : $1 (attendu : --link, --voices <url>)"; ;;
  esac
done

if [ "$MODE" = "link" ]; then
  donner_lien
  exit 0
fi

# --- 1. Ce qui doit exister ---------------------------------------------------------------

etape "1/5  Ce qui doit être là"
command -v cloudflared >/dev/null 2>&1 || die "cloudflared manque. Installe-le :
    curl -L -o ~/.local/bin/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
    chmod +x ~/.local/bin/cloudflared"
ok "cloudflared présent"

command -v systemctl >/dev/null 2>&1 || die "systemctl est nécessaire (services utilisateur)."
ok "systemd utilisateur disponible"

if curl -s -o /dev/null -m 5 -H "Authorization: Bearer x" "http://127.0.0.1:${PORT_API}/v1/models"; then
  ok "Hermes répond sur ${PORT_API}"
else
  die "Hermes ne répond pas sur 127.0.0.1:${PORT_API}.
    Active l'exposition pour l'app dans ~/.hermes/config.yaml :

      platforms:
        api_server:
          enabled: true
          host: 127.0.0.1
          port: ${PORT_API}
          key: <une clé de ton choix>

    puis relance la passerelle : hermes gateway${IFS}restart"
fi

# --- 2. Les secrets, tirés une fois ------------------------------------------------------

etape "2/5  Les secrets locaux"
if [ -f "$CLE_FICHIER" ]; then
  ok "clé de l'app déjà présente (inchangée)"
else
  ( umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$CLE_FICHIER" )
  ok "clé de l'app créée"
fi

if [ -f "$JETON_FICHIER" ]; then
  ok "jeton du dashboard déjà présent (inchangé)"
else
  ( umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$JETON_FICHIER" )
  ok "jeton du dashboard créé"
fi

if [ -f "$MDP_FICHIER" ]; then
  ok "mot de passe du dashboard déjà présent (inchangé)"
else
  ( umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(24))' > "$MDP_FICHIER" )
  ok "mot de passe du dashboard créé"
fi

# L'empreinte scrypt est recalculée à chaque fois : elle n'est pas un secret, et la recalculer
# évite qu'un mot de passe changé laisse un dashboard fermé.
( umask 077
  python3 - "$MDP_FICHIER" "$JETON_FICHIER" "$ENV_DASH" <<'PY'
import base64, hashlib, secrets, sys
mdp, jeton, dest = sys.argv[1], sys.argv[2], sys.argv[3]
pw = open(mdp).read().strip()
sel = secrets.token_bytes(16)
dk = hashlib.scrypt(pw.encode(), salt=sel, n=2**14, r=8, p=1, dklen=32, maxmem=0)
empreinte = "scrypt$16384$8$1$%s$%s" % (base64.b64encode(sel).decode(), base64.b64encode(dk).decode())
with open(dest, "w") as fh:
    fh.write("HERMES_DASHBOARD_BASIC_AUTH_USERNAME=onyx\n")
    fh.write(f"HERMES_DASHBOARD_BASIC_AUTH_PASSWORD_HASH={empreinte}\n")
    fh.write(f"HERMES_DASHBOARD_SESSION_TOKEN={open(jeton).read().strip()}\n")
PY
)
chmod 600 "$ENV_DASH"
ok "identifiants du dashboard écrits (0600)"

# --- 3. Les services ----------------------------------------------------------------------

etape "3/5  Les services"
mkdir -p "$UNITES"

ecrire_unite() {  # nom, contenu
  local nom="$1" contenu="$2"
  if [ -f "${UNITES}/${nom}" ] && [ "$(cat "${UNITES}/${nom}")" = "$contenu" ]; then
    ok "${nom} déjà à jour"
  else
    printf '%s' "$contenu" > "${UNITES}/${nom}"
    ok "${nom} écrit"
  fi
}

ecrire_unite "onyx-dashboard.service" "[Unit]
Description=Hermes Dashboard headless (hermes serve) — les routes /api du dashboard, pour l'app Onyx
After=network-online.target hermes-gateway.service
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=${HOME}/.hermes
EnvironmentFile=${ENV_DASH}
ExecStart=$(command -v hermes) serve --port ${PORT_DASHBOARD} --host 127.0.0.1
Restart=always
RestartSec=3

[Install]
WantedBy=default.target
"

ecrire_unite "onyx-one.service" "[Unit]
Description=Onyx One — passerelle « une clé » devant Hermes (découverte + /api + /v1)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=${RACINE}
Environment=ONYX_ONE_HOST=127.0.0.1
Environment=ONYX_ONE_PORT=${PORT_PASSERELLE}
Environment=ONYX_ONE_KEY_FILE=${CLE_FICHIER}
Environment=ONYX_ONE_DASH_TOKEN_FILE=${JETON_FICHIER}
Environment=ONYX_ONE_VOICES=auto
Environment=ONYX_ONE_LANG=fr
Environment=ONYX_ONE_MODEL=hermes-agent
ExecStart=/usr/bin/python3 ${RACINE}/onyx-one.py
Restart=always
RestartSec=3

[Install]
WantedBy=default.target
"

ecrire_unite "onyx-one-tunnel.service" "[Unit]
Description=Tunnel public de la passerelle Onyx (port ${PORT_PASSERELLE})
After=network-online.target onyx-one.service
Wants=onyx-one.service

[Service]
ExecStart=$(command -v cloudflared) tunnel --url http://127.0.0.1:${PORT_PASSERELLE} --no-autoupdate --logfile ${RACINE}/tunnel.log --loglevel info
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
"

ecrire_unite "onyx-tunnel-watchdog.service" "[Unit]
Description=Chien de garde des tunnels Onyx (regénère le lien quand le nom public change)

[Service]
Type=oneshot
ExecStart=/usr/bin/bash ${RACINE}/watch-tunnels.sh
"

# --- 3b. Le pont « Ordinateur » (l'écran du PC) -------------------------------------------
#
# Il ne vient pas de Hermes : c'est un service compagnon, livré dans le dépôt de l'app. Sans lui,
# l'écran « Ordinateur » n'a rien à qui parler — l'app affiche « pont injoignable » et l'utilisateur
# n'a aucun moyen de savoir pourquoi.

PONT_SRC="${SCRIPT_DIR}/onyx-computer-bridge"
PONT_DIR="${HOME}/.onyx-computer/tools/onyx-computer-bridge"
PONT_JETON_DIR="${HOME}/.onyx-computer-bridge"

if [ -d "$PONT_SRC" ]; then
  mkdir -p "${HOME}/.onyx-computer/tools"
  if ! cmp -s "${PONT_SRC}/bridge.py" "${PONT_DIR}/bridge.py" 2>/dev/null; then
    cp -a "$PONT_SRC" "${HOME}/.onyx-computer/tools/"
    ok "code du pont installé"
  else
    ok "code du pont déjà à jour"
  fi

  if [ ! -x "${PONT_JETON_DIR}/venv/bin/python" ]; then
    /usr/bin/python3 -m venv "${PONT_JETON_DIR}/venv" >/dev/null 2>&1 || true
  fi
  "${PONT_JETON_DIR}/venv/bin/python" -m pip install --quiet --disable-pip-version-check \
    python-xlib websockets >/dev/null 2>&1 && ok "dépendances du pont présentes" \
    || attention "dépendances du pont non installées (l'écran Ordinateur en a besoin)"

  if [ ! -s "${PONT_JETON_DIR}/token" ]; then
    mkdir -p "$PONT_JETON_DIR"
    ( umask 077; python3 -c 'import secrets; print(secrets.token_hex(24))' > "${PONT_JETON_DIR}/token" )
    ok "jeton du pont créé"
  fi

  ecrire_unite "onyx-computer-bridge.service" "[Unit]
Description=Pont « Ordinateur » — écran du PC et entrées (app Onyx)
After=graphical-session.target

[Service]
Type=simple
WorkingDirectory=${PONT_DIR}
ExecStart=${PONT_JETON_DIR}/venv/bin/python ${PONT_DIR}/bridge.py --display ${AFFICHAGE} --port 8795 --fps 6 --turbo-fps 12
Restart=always
RestartSec=3
Environment=PYTHONUNBUFFERED=1
Environment=DISPLAY=${AFFICHAGE}

[Install]
WantedBy=default.target
"

  ecrire_unite "onyx-computer-tunnel.service" "[Unit]
Description=Tunnel public du pont « Ordinateur » (app Onyx)
After=network-online.target onyx-computer-bridge.service
Wants=onyx-computer-bridge.service

[Service]
ExecStart=$(command -v cloudflared) tunnel --url http://127.0.0.1:8795 --no-autoupdate --logfile ${PONT_JETON_DIR}/tunnel.log --loglevel info
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
"

  systemctl --user enable --now onyx-computer-bridge.service >/dev/null 2>&1 || true
  if systemctl --user is-active --quiet onyx-computer-bridge.service; then
    systemctl --user restart onyx-computer-bridge.service >/dev/null 2>&1 || true
  fi
  if systemctl --user is-active --quiet onyx-computer-tunnel.service; then
    systemctl --user enable onyx-computer-tunnel.service >/dev/null 2>&1 || true
  else
    systemctl --user enable --now onyx-computer-tunnel.service >/dev/null 2>&1 || true
  fi
  ok "pont « Ordinateur » et son tunnel installés"
else
  attention "code du pont absent (tools/onyx-computer-bridge) — l'écran Ordinateur restera muet"
fi

ecrire_unite "onyx-tunnel-watchdog.timer" "[Unit]
Description=Vérifie le nom public des tunnels Onyx toutes les deux minutes

[Timer]
OnBootSec=90
OnUnitActiveSec=120
Unit=onyx-tunnel-watchdog.service

[Install]
WantedBy=timers.target
"

systemctl --user daemon-reload
for u in onyx-dashboard onyx-one; do
  systemctl --user enable --now "${u}.service" >/dev/null 2>&1 || true
  systemctl --user restart "${u}.service" >/dev/null 2>&1 || true
done

# **Le tunnel ne se redémarre que s'il est mort.** Son nom public change à chaque démarrage :
# relancer un tunnel qui va bien, c'est invalider le lien déjà installé sur le téléphone — et
# rendre le script non idempotent alors qu'il se veut rejouable sans conséquence.
if systemctl --user is-active --quiet onyx-one-tunnel.service; then
  systemctl --user enable onyx-one-tunnel.service >/dev/null 2>&1 || true
else
  systemctl --user enable --now onyx-one-tunnel.service >/dev/null 2>&1 || true
fi
systemctl --user enable --now onyx-tunnel-watchdog.timer >/dev/null 2>&1 || true
ok "les trois services et le chien de garde sont actifs"

# --- 4. Attendre les tunnels --------------------------------------------------------------

etape "4/5  Les tunnels publics"
# **Le journal se lit là où le service écrit vraiment.** Une surcharge d'unité (`*.service.d/`)
# peut déplacer `--logfile` et envoyer l'adresse ailleurs ; on demande donc à systemd sa ligne de
# commande effective au lieu de supposer le chemin.
journal_tunnel() {
  local ligne
  ligne="$(systemctl --user show onyx-one-tunnel.service -p ExecStart 2>/dev/null || true)"
  local chemin
  chemin="$(printf '%s' "$ligne" | grep -oE -- '--logfile [^ ;"]+' | awk '{print $2}' | head -1)"
  printf '%s' "${chemin:-${RACINE}/tunnel.log}"
}
LOG_TUNNEL="$(journal_tunnel)"
ok "journal du tunnel : ${LOG_TUNNEL}"

serveur=""
for _ in $(seq 1 30); do
  serveur="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG_TUNNEL" 2>/dev/null | tail -1 || true)"
  [ -n "$serveur" ] && break
  sleep 2
done
[ -n "$serveur" ] || die "Le tunnel n'a pas publié d'adresse en 60 s. Journal : ${LOG_TUNNEL}"

# **Le nom publié n'est pas le tunnel prêt.** Cloudflare annonce l'adresse dans le journal avant
# que l'arête ne la serve : donner le lien à cet instant produit un « 530 » chez l'utilisateur.
# On attend une vraie réponse (/health) avant de dire que le tunnel est là.
pret=0
for _ in $(seq 1 30); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' -m 8 "${serveur}/health" 2>/dev/null || true)" = "200" ]; then
    pret=1; break
  fi
  sleep 2
done
if [ "$pret" = "1" ]; then
  ok "tunnel de la passerelle : ${serveur} (il répond)"
else
  attention "le tunnel publie son adresse mais ne répond pas encore : ${serveur}"
  attention "le lien ci-dessous sera valable dans quelques secondes"
fi

# --- 5. Le lien et son QR -----------------------------------------------------------------

etape "5/5  Le lien de configuration"
if [ ! -x "${VENV}/bin/python" ]; then
  python3 -m venv "$VENV" >/dev/null 2>&1 || true
fi
"${VENV}/bin/python" -m pip install --quiet --disable-pip-version-check segno >/dev/null 2>&1 \
  && ok "QR disponible" || attention "segno n'a pas pu s'installer : le lien sera affiché sans QR"

donner_lien

cat <<TXT

  Sur le téléphone :
    1. installe l'APK de la dernière release (github.com/JemaOS/hermes-onyx/releases/latest) ;
    2. au premier écran, scanne le QR ci-dessus — ou colle le lien ;
    3. c'est tout : la conversation, les voix, les compétences et les outils arrivent ensemble.

  Pour redonner ce lien plus tard : $0 --link
  Les journaux : journalctl --user -u onyx-one -u onyx-dashboard -u onyx-one-tunnel -f

TXT
