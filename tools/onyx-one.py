#!/usr/bin/env python3
"""Onyx One — la passerelle « une clé » devant Hermes, pour l'app Onyx.

Ce que l'app envoie, et ce que ce relais doit répondre (relevé sur son propre trafic) :

    GET  /v1/models              → 200, la liste des modèles     (iro api_server :8642)
    GET  /api/sessions           → 200, le registre                (api_server :8642)
    GET  /api/profiles           → 200, {"profiles":[…]}           (lu sur le disque)
    GET  /api/skills             → 200, {"skills":[…]}             (lu sur le disque)
    POST /api/auth/ws-ticket     → 200, {"ticket":…,"ttl_seconds":…}
    GET  /api/onyx/endpoints     → 200, le manifeste de connexion

Une seule clé : celle du lien `onyx://connect`. Elle est exigée sur **tout**, sauf la sonde de
santé (`/api/status`), pour que Cloudflare puisse constater que le service est debout.

Le 404 est ce qui a cassé l'app : elle demande `/api/profiles` et `/api/auth/ws-ticket`, reçoit
`404`, et en conclut que la passerelle n'est pas là — alors que `/v1/models` venait de lui répondre.
Un relais qui répond **200** à ces routes, même avec une liste vide, la laisse continuer.
"""

from __future__ import annotations

import http.client
import json
import os
import re
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

# --- Réglages ------------------------------------------------------------------------------

PORT = int(os.environ.get("ONYX_ONE_PORT", "8890"))
HOTE = os.environ.get("ONYX_ONE_HOST", "127.0.0.1")

CLE_FICHIER = os.environ.get("ONYX_ONE_KEY_FILE", "/home/john/.onyx-one-key")
API_HOTE, API_PORT = "127.0.0.1", 8642          # hermes gateway api_server (/v1, /api/sessions)
DASH_HOTE, DASH_PORT = "127.0.0.1", 9119        # hermes serve : les routes /api du dashboard
# Le plugin `onyx-browser` : son propre serveur, jamais exposé. C'est **ce relais** qui le met
# derrière l'adresse et la clé que l'app connaît déjà — aucun tunnel de plus, aucun port ouvert.
BROWSER_HOTE, BROWSER_PORT = "127.0.0.1", 8788
BROWSER_PREFIXE = "/onyx-browser/"
# **Le navigateur n'est pas toujours sur 8788.** Hermes fait tourner plusieurs processus qui
# chargent le plugin, et le navigateur de l'agent vit dans celui qui exécute le tour — souvent
# `hermes serve --port 9119`, pas celui qui a pris 8788 en premier. Chacun dépose une fiche
# (`{pid, port, pending, browser, ts}`) et le relais envoie l'écran au bon : d'abord celui qui a
# une demande de prise en main en attente, sinon celui qui a un navigateur ouvert, sinon 8788.
BROWSER_PEERS = os.environ.get(
    "ONYX_ONE_BROWSER_PEERS", os.path.join(os.path.expanduser("~/.hermes"), "onyx-browser", "peers"))
BROWSER_PEER_TTL = 20.0
# Ticket du plugin → port qui l'a émis. `observe` et le WebSocket sont deux requêtes séparées :
# sans cette épingle, un tour qui se termine entre les deux ferait présenter le ticket au mauvais
# processus, et la poignée de main finirait en 401 sans que rien ne l'explique.
_TICKETS: dict[str, tuple[float, int]] = {}

RACINE = os.path.expanduser("~/.hermes")
PROFILS = os.path.join(RACINE, "profiles")

VOIX = os.environ.get("ONYX_ONE_VOICES", "")
PONT = os.environ.get("ONYX_ONE_BRIDGE", "")
PONT_JETON = os.environ.get("ONYX_ONE_BRIDGE_TOKEN", "")
LANGUE = os.environ.get("ONYX_ONE_LANG", "fr")
MODELE = os.environ.get("ONYX_ONE_MODEL", "hermes-agent")

# En-têtes qu'on ne recopie jamais dans les deux sens : ils appartiennent au saut en cours.
SAUT = {"host", "connection", "content-length", "transfer-encoding", "keep-alive",
        "proxy-authenticate", "proxy-authorization", "te", "trailers", "upgrade"}


def _sante(port: int) -> dict:
    """Ce que ce processus dit de lui-même, ou `{}` s'il ne répond pas.

    Une fiche de pair est une **déclaration** : elle dit ce que le processus croyait au moment où
    il l'a écrite. Un processus lancé avant l'installation du registre n'en écrit aucune — il
    tient pourtant le navigateur et la demande en attente. On lui demande donc directement, ce
    qui ne dépend ni de sa version, ni de la fraîcheur d'un fichier.
    """
    try:
        cx = http.client.HTTPConnection(BROWSER_HOTE, port, timeout=0.4)
        try:
            cx.request("GET", BROWSER_PREFIXE + "health")
            reponse = cx.getresponse()
            if reponse.status != 200:
                return {}
            charge = reponse.read()
        finally:
            cx.close()
    except OSError:
        return {}
    try:
        sante = json.loads(charge or b"{}")
    except ValueError:
        return {}
    return sante if isinstance(sante, dict) else {}


def _retenir_ticket(charge: bytes, port: int) -> None:
    """Épingle le ticket sur le processus qui l'a émis.

    `observe` et le WebSocket sont deux requêtes distinctes : si la sélection change entre les
    deux — un tour qui se termine, un autre qui s'ouvre — le ticket serait présenté au mauvais
    processus et la poignée de main finirait en 401. Le ticket ne vaut que sur son émetteur.
    """
    try:
        ticket = (json.loads(charge or b"{}") or {}).get("ticket")
    except ValueError:
        return
    if not ticket:
        return
    maintenant = time.time()
    for cle in [k for k, (quand, _) in _TICKETS.items() if maintenant - quand > 40]:
        _TICKETS.pop(cle, None)
    _TICKETS[str(ticket)] = (maintenant, port)


def _ticket_de(chemin: str) -> int:
    """Le port épinglé pour le ticket de cette URL, ou 0."""
    from urllib.parse import parse_qs

    ticket = (parse_qs(urlsplit(chemin).query).get("ticket") or [""])[0]
    quand_port = _TICKETS.get(ticket)
    if not quand_port or time.time() - quand_port[0] > 40:
        return 0
    return quand_port[1]


def _navigateur_a_joindre() -> tuple[str, int]:
    """Quel processus tient le navigateur de l'agent ?

    Deux sources, et la seconde tranche : les fiches déposées par chaque copie du plugin
    (`~/.hermes/onyx-browser/peers/<pid>.json`), puis une **question directe** à chaque candidat
    (`/onyx-browser/health`). On préfère, dans l'ordre :

      1. celui qui a une **prise en main en attente** — c'est là que l'écran doit arriver ;
      2. celui qui a un **navigateur ouvert** dans son tour ;
      3. le port historique 8788 ;
      4. le plus récent.

    Tomber sur le mauvais processus n'est pas une erreur visible : l'app verrait un écran qui
    attend indéfiniment. C'est précisément le défaut qu'on corrige ici — une fiche absente ne doit
    pas rendre un processus invisible.
    """
    fiches: list[dict] = []
    try:
        noms = os.listdir(BROWSER_PEERS)
    except OSError:
        noms = []
    maintenant = time.time()
    for nom in noms:
        if not nom.endswith(".json"):
            continue
        try:
            with open(os.path.join(BROWSER_PEERS, nom), encoding="utf-8") as flux:
                fiche = json.load(flux)
            port = int(fiche.get("port") or 0)
            pid = int(fiche.get("pid") or 0)
            age = maintenant - float(fiche.get("ts") or 0)
        except (OSError, ValueError, TypeError):
            continue
        if not port or age > BROWSER_PEER_TTL:
            continue
        if pid:
            try:
                os.kill(pid, 0)
            except OSError:
                continue
        fiches.append(fiche)

    # **La question directe, et 8788 toujours candidate.** Un processus qui tient le navigateur
    # mais n'écrit pas de fiche (lancé avant l'installation du registre) doit rester joignable :
    # c'est exactement le cas qui a laissé l'écran du téléphone sur un processus vide.
    candidats: list[int] = []
    for port in [int(f.get("port") or 0) for f in fiches] + [BROWSER_PORT]:
        if port and port not in candidats:
            candidats.append(port)
    if not candidats:
        return BROWSER_HOTE, BROWSER_PORT

    meilleur: tuple[int, int, float] | None = None  # (score, préférence 8788, fraîcheur)
    gagnant = BROWSER_PORT
    for port in candidats:
        sante = _sante(port)
        if not sante.get("ok"):
            continue
        score = 3 if sante.get("pending") else (2 if sante.get("browser") else 1)
        fraicheur = max([float(f.get("ts") or 0) for f in fiches if int(f.get("port") or 0) == port]
                        or [0.0])
        cle = (score, 1 if port == BROWSER_PORT else 0, fraicheur)
        if meilleur is None or cle > meilleur:
            meilleur = cle
            gagnant = port
    if meilleur is not None:
        return BROWSER_HOTE, gagnant

    # Aucun candidat ne répond : on garde l'ancienne heuristique sur les fiches seules.
    if not fiches:
        return BROWSER_HOTE, BROWSER_PORT
    en_attente = [f for f in fiches if f.get("pending")]
    if en_attente:
        return BROWSER_HOTE, int(max(en_attente, key=lambda f: float(f.get("ts") or 0))["port"])
    ouverts = [f for f in fiches if f.get("browser")]
    if ouverts:
        return BROWSER_HOTE, int(max(ouverts, key=lambda f: float(f.get("ts") or 0))["port"])
    return BROWSER_HOTE, int(max(fiches, key=lambda f: float(f.get("ts") or 0))["port"])


def _cle() -> str:
    try:
        with open(CLE_FICHIER, encoding="utf-8") as fh:
            cle = fh.read().strip()
    except FileNotFoundError:
        sys.exit(f"onyx-one : clé introuvable ({CLE_FICHIER})")
    if len(cle) < 16:
        sys.exit("onyx-one : clé trop courte dans " + CLE_FICHIER)
    return cle


CLE = _cle()


def _jeton_dashboard() -> str:
    """Le jeton de session du dashboard local (`hermes serve`), quand il tourne.

    Le dashboard en boucle locale ne réclame pas de mot de passe : il demande l'en-tête
    `X-Hermes-Session-Token`, tiré au démarrage. Onyx One le lit ici et le présente à chaque
    relais — c'est ce qui débloque les routes que l'app demande et que l'api_server n'a pas :
    `api/cron/jobs`, `api/tools/toolsets`, `api/mcp/servers`, `api/system/stats`…
    """
    try:
        with open(os.environ.get("ONYX_ONE_DASH_TOKEN_FILE",
                                 "/home/john/.onyx-one-dashboard-token"), encoding="utf-8") as fh:
            return fh.read().strip()
    except OSError:
        return ""


JETON_DASHBOARD = _jeton_dashboard()


def _cle_api_server() -> str:
    """La clé que l'`api_server` de Hermes attend, lue dans **sa** configuration.

    L'app ne la connaît pas et n'a pas à la connaître : elle présente la clé d'installation
    (une seule, la sienne), et c'est Onyx One qui parle à l'api_server avec la clé de celui-ci.
    Les deux n'ont donc aucune raison d'être égales — et un nouvel utilisateur n'a rien à
    synchroniser à la main.

    La lecture est **structurelle** quand PyYAML est là : le bloc peut vivre sous `platforms`,
    sous `gateway`, ou ailleurs, et deviner sa place par l'indentation a déjà produit un second
    bloc concurrent sur une installation existante.
    """
    chemin = os.environ.get("ONYX_ONE_HERMES_CONFIG",
                            os.path.expanduser("~/.hermes/config.yaml"))
    try:
        import yaml  # présent avec /usr/bin/python3 ; sinon on retombe sur la lecture textuelle
    except ImportError:
        yaml = None
    try:
        with open(chemin, encoding="utf-8", errors="replace") as fh:
            brut = fh.read()
    except OSError:
        return CLE

    if yaml is not None:
        try:
            cfg = yaml.safe_load(brut) or {}

            def trouver(noeud):
                if isinstance(noeud, dict):
                    for nom, valeur in noeud.items():
                        if nom == "api_server" and isinstance(valeur, dict):
                            cle = valeur.get("key")
                            if isinstance(cle, str) and cle.strip():
                                return cle.strip()
                        trouve = trouver(valeur)
                        if trouve:
                            return trouve
                return None

            trouve = trouver(cfg)
            if trouve:
                return trouve
        except Exception:  # noqa: BLE001 — une config illisible ne doit pas tuer la passerelle
            pass

    # Repli : le premier `key:` d'un bloc indenté nommé `api_server:`.
    dans_bloc = False
    for ligne in brut.splitlines():
        if ligne.strip() == "api_server:":
            dans_bloc = True
            continue
        if dans_bloc:
            if ligne and not ligne[0].isspace():
                dans_bloc = False
                continue
            m = re.match(r"^\s+key\s*:\s*(\S+)\s*$", ligne)
            if m:
                return m.group(1).strip("\"'")
    return CLE


CLE_API = _cle_api_server()


# --- Données lues sur le disque ------------------------------------------------------------

def profils() -> list[dict]:
    """Les profils Hermes, lus dans `~/.hermes/profiles` (le défaut à part)."""
    rows = [{"name": "default", "path": RACINE, "is_default": True, "hidden": False}]
    try:
        for nom in sorted(os.listdir(PROFILS)):
            chemin = os.path.join(PROFILS, nom)
            if not os.path.isdir(chemin) or nom.startswith("."):
                continue
            rows.append({"name": nom, "path": chemin, "is_default": False, "hidden": False})
    except FileNotFoundError:
        pass
    return rows


def competences() -> list[dict]:
    """Les compétences installées : un SKILL.md par dossier, dans chaque racine connue."""
    vues, rows = set(), []
    for racine in ("skills", os.path.join("profiles", "kilo", "skills")):
        base = os.path.join(RACINE, racine)
        if not os.path.isdir(base):
            continue
        for dossier, _, fichiers in os.walk(base):
            if "SKILL.md" not in fichiers:
                continue
            nom = os.path.basename(dossier)
            if nom in vues:
                continue
            vues.add(nom)
            rows.append({
                "name": nom,
                "path": os.path.relpath(dossier, base),
                "description": _description(os.path.join(dossier, "SKILL.md")),
                "enabled": True,
                "source": "local",
            })
    return rows


def _description(chemin: str) -> str:
    try:
        with open(chemin, encoding="utf-8") as fh:
            for ligne in fh:
                if ligne.lower().startswith("description:"):
                    return ligne.split(":", 1)[1].strip().strip('"')[:200]
    except OSError:
        pass
    return ""


def _voix_vivante() -> str:
    """L'adresse des voix, **relue sur le disque** à chaque demande.

    Un tunnel jetable change de nom quand `cloudflared` redémarre. Écrire son adresse ici une fois
    pour toutes, c'est garantir qu'elle sera fausse un jour — et l'app, elle, garde ce que le
    manifeste lui a dit. On lit donc la dernière adresse qu'il a publiée.
    """
    if VOIX and VOIX != "auto":
        return VOIX
    try:
        with open("/home/john/kokoro-tts/tunnel.log", encoding="utf-8", errors="replace") as fh:
            urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", fh.read())
        return urls[-1] if urls else ""
    except OSError:
        return ""


def _pont_vivant() -> str:
    """L'adresse publique du pont « Ordinateur », relue à chaque demande (même raison que les voix)."""
    if PONT and PONT != "auto":
        return PONT
    try:
        with open("/home/john/.onyx-computer-bridge/tunnel.log", encoding="utf-8", errors="replace") as fh:
            urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", fh.read())
        return urls[-1] if urls else ""
    except OSError:
        return ""


def _jeton_pont() -> str:
    """Le jeton du pont, lu dans son fichier 0600 — jamais recopié ailleurs."""
    if PONT_JETON:
        return PONT_JETON
    try:
        with open("/home/john/.onyx-computer-bridge/token", encoding="utf-8") as fh:
            return fh.read().strip()
    except OSError:
        return ""


def manifeste() -> dict:
    return {
        "version": 1,
        "server": f"http://{HOTE}:{PORT}",
        "model": MODELE,
        "voices": _voix_vivante(),
        "bridge": _pont_vivant(),
        "bridgeToken": _jeton_pont(),
        "language": LANGUE,
        "capabilities": ["sessions", "voices", "skills", "profiles"],
    }


# --- Le relais ------------------------------------------------------------------------------

class Relais(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        return

    # -- utilitaires --

    def _repondre(self, statut: int, charge: object) -> None:
        corps = json.dumps(charge, ensure_ascii=False).encode()
        self.send_response(statut)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def _autorise(self) -> bool:
        brut = (self.headers.get("Authorization") or "").strip()
        if brut[:7].lower() == "bearer ":
            brut = brut[7:].strip()
        if not brut:
            brut = (self.headers.get("X-API-Key") or "").strip()
        return bool(brut) and _constant(brut, CLE)

    def _vers_amont(self, hote: str, port: int, entete_auth: dict[str, str],
                    corps: bytes | None) -> tuple[int, list[tuple[str, str]], bytes]:
        """Un aller-retour vers l'amont, avec l'identité que le relais présente à sa place."""
        entetes = {}
        for nom, valeur in self.headers.items():
            if nom.lower() in SAUT or nom.lower() in ("authorization", "x-api-key",
                                                      "x-hermes-session-token"):
                continue
            entetes[nom] = valeur
        entetes.update(entete_auth)
        cx = http.client.HTTPConnection(hote, port, timeout=300)
        try:
            cx.request(self.command, self.path, body=corps, headers=entetes)
            reponse = cx.getresponse()
            return reponse.status, [(k, v) for k, v in reponse.getheaders()], reponse.read()
        finally:
            cx.close()

    # -- routage --

    def _servir(self) -> None:
        chemin = urlsplit(self.path).path
        longueur = int(self.headers.get("Content-Length") or 0)
        corps = self.rfile.read(longueur) if longueur else None

        if chemin in ("/health", "/healthz"):
            return self._repondre(200, {"status": "ok"})
        if chemin == "/api/status":
            return self._repondre(200, {"version": "0.22.3", "auth_required": True,
                                        "gateway_running": True, "onyx_one": True})
        if not self._autorise():
            return self._repondre(401, {"error": "unauthenticated", "detail": "Unauthorized"})

        if chemin == "/api/onyx/endpoints":
            return self._repondre(200, manifeste())

        # **Deux amonts, et la frontière est celle de l'app.**
        #
        #   api_server (8642) : le chat — `/v1/*` et le registre des conversations.
        #   dashboard  (9119) : tout le reste de `/api` — cron, outils, MCP, stats, compétences.
        #
        # Les deux sont publics seulement dans cette machine ; c'est Onyx One qui porte l'unique
        # clé de l'app et présente à chacun l'identité qu'il attend (Bearer d'un côté, jeton de
        # session de l'autre). L'app, elle, ne connaît qu'une adresse et qu'une clé.
        vers_api = chemin.startswith("/v1/") or chemin.startswith("/api/sessions")
        vers_navigateur = chemin.startswith(BROWSER_PREFIXE)
        if self.command in ("GET", "POST") and (self.headers.get("Upgrade") or "").lower() == "websocket":
            if vers_navigateur:
                # **Le ticket désigne son processus.** S'il a été épinglé par l'`observe` qui
                # l'a précédé, on y va directement : la sélection n'a pas le droit de changer
                # entre les deux requêtes.
                epingle = _ticket_de(self.path)
                if epingle:
                    return self._relayer_websocket(chemin, BROWSER_HOTE, epingle)
                hote_nav, port_nav = _navigateur_a_joindre()
                return self._relayer_websocket(chemin, hote_nav, port_nav)
            return self._relayer_websocket(chemin)

        if vers_navigateur:
            # Le ticket du plugin n'est pas une identité pour lui : c'est celui de l'app. Le plugin
            # accepte la boucle locale, et son port n'écoute que là — il n'a rien à vérifier de plus.
            #
            # **Un amont muet ne tue pas la requête.** Le plugin n'écoute qu'une fois la passerelle
            # démarrée ; avant ça, `http.client` lève, et sans ce garde la connexion se fermait sans
            # réponse (l'app voyait « rien », pas « absent »). On répond 503, ce que la sonde de
            # capacité comprend : la ligne n'apparaît pas, et rien ne ment.
            try:
                hote_nav, port_nav = _navigateur_a_joindre()
                statut, entetes, charge = self._vers_amont(hote_nav, port_nav, {}, corps)
                if statut == 200 and chemin.rstrip("/").endswith("/observe"):
                    _retenir_ticket(charge, port_nav)
            except OSError:
                return self._repondre(503, {"error": "browser_unavailable",
                                            "detail": "no onyx-browser plugin is listening"})
        elif vers_api:
            statut, entetes, charge = self._vers_amont(
                API_HOTE, API_PORT, {"Authorization": "Bearer " + CLE_API}, corps)
        elif JETON_DASHBOARD:
            statut, entetes, charge = self._vers_amont(
                DASH_HOTE, DASH_PORT, {"X-Hermes-Session-Token": JETON_DASHBOARD}, corps)
            # Le dashboard ne sait pas émettre de ticket pour un client sans cookie : on le fait.
            if chemin == "/api/auth/ws-ticket" and statut in (401, 404):
                return self._repondre(200, {"ticket": uuid.uuid4().hex, "ttl_seconds": 60})
        else:
            return self._repondre(503, {"error": "dashboard_unavailable",
                                        "detail": "hermes serve is not running on 9119"})

        self.send_response(statut)
        for nom, valeur in entetes:
            if nom.lower() in SAUT:
                continue
            self.send_header(nom, valeur)
        self.send_header("Content-Length", str(len(charge)))
        self.end_headers()
        self.wfile.write(charge)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_OPTIONS = _servir


    def _relayer_websocket(self, chemin: str, hote: str = DASH_HOTE,
                           port: int = DASH_PORT) -> None:
        """Une connexion WebSocket, relayée **octet pour octet**.

        `http.server` ne sait pas négocier une mise à niveau ; on prend donc la socket telle
        qu'elle est et on la relie à l'amont, en glissant l'en-tête d'identité du dashboard dans
        la requête de mise à niveau. C'est ce qui donne à l'app son flux d'événements vivant.
        """
        import select
        import socket

        amont = socket.create_connection((hote, port), timeout=10)
        try:
            # Le ticket de l'app ne veut rien dire pour le dashboard (il est faux — voir
            # `api/auth/ws-ticket`) : en boucle locale, c'est `?token=` qui ouvre le socket. On
            # remplace donc le paramètre au passage.
            import urllib.parse as _up
            morceaux = _up.urlsplit(self.path)
            parametres = [(k, v) for k, v in _up.parse_qsl(morceaux.query, keep_blank_values=True)
                          if k != "ticket"]
            parametres.append(("token", JETON_DASHBOARD))
            if (hote, port) == (DASH_HOTE, DASH_PORT):
                chemin_amont = morceaux.path + "?" + _up.urlencode(parametres)
            else:
                # Le plugin du navigateur ne connaît pas le jeton du dashboard : le ticket, lui,
                # reste tel quel — c'est le sien.
                chemin_amont = self.path
            lignes = [f"GET {chemin_amont} HTTP/1.1", f"Host: {hote}:{port}"]
            # **On garde la mise à niveau.** `SAUT` sert au relais HTTP ordinaire ; ici, retirer
            # `Upgrade`/`Connection` transformerait la poignée de main en GET simple, et le
            # dashboard répondrait 401 au lieu de 101.
            garder = {"upgrade", "connection", "sec-websocket-key", "sec-websocket-version",
                      "sec-websocket-protocol", "sec-websocket-extensions", "origin"}
            for nom, valeur in self.headers.items():
                if nom.lower() in ("authorization", "x-api-key", "host"):
                    continue
                if nom.lower() in SAUT and nom.lower() not in garder:
                    continue
                lignes.append(f"{nom}: {valeur}")
            amont.sendall(("\r\n".join(lignes) + "\r\n\r\n").encode())
            print(f"WS -> {hote}:{port}{chemin_amont}", flush=True)

            client = self.connection
            client.setblocking(False)
            amont.setblocking(False)
            while True:
                lisibles, _, en_erreur = select.select([client, amont], [], [client, amont], 60)
                if en_erreur:
                    break
                for source in lisibles:
                    autre = amont if source is client else client
                    try:
                        donnees = source.recv(65536)
                    except BlockingIOError:
                        continue
                    if not donnees:
                        return
                    autre.sendall(donnees)
        finally:
            amont.close()
            self.close_connection = True


def _constant(a: str, b: str) -> bool:
    import hmac
    return hmac.compare_digest(a.encode(), b.encode())


if __name__ == "__main__":
    srv = ThreadingHTTPServer((HOTE, PORT), Relais)
    srv.daemon_threads = True
    print(f"onyx-one sur {HOTE}:{PORT} -> api_server {API_HOTE}:{API_PORT}", flush=True)
    srv.serve_forever()
