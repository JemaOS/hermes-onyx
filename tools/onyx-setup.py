#!/usr/bin/env python3
"""Onyx — premier démarrage, une commande, sur Linux, macOS et Windows.

Ce script fait ce qu'un nouvel utilisateur ne peut pas deviner :

  1. vérifie ce qui doit exister (Hermes, cloudflared) et **l'installe si besoin** ;
  2. active l'exposition de Hermes à l'app (`api_server`), en n'ajoutant que ce qui manque ;
  3. tire **une** clé et les secrets locaux, en 0600 ;
  4. installe les composants comme services du système — systemd sous Linux, launchd sous macOS,
     une tâche à l'ouverture de session sous Windows — pour qu'ils reviennent seuls ;
  5. attend que le tunnel **réponde vraiment**, puis affiche le lien `onyx://connect` et son QR.

Il est rejouable : relancé, il ne change ni la clé, ni le tunnel, ni le lien déjà installé.

    python3 tools/onyx-setup.py                 installe / répare, puis donne le lien
    python3 tools/onyx-setup.py --link          redonne seulement le lien courant
    python3 tools/onyx-setup.py --voices URL    adresse d'un serveur TTS Kokoro-compatible

Ce qui ne suit pas partout : le pont « Ordinateur » capture un écran X11 — il n'existe que sous
Linux. Ailleurs, l'écran Ordinateur de l'app restera vide, et le script le dit au lieu de
l'installer à moitié.
"""

from __future__ import annotations

import argparse
import os
import platform
import re
import secrets
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

RACINE = Path.home() / ".onyx-one"
CLE = RACINE / "cle"
JETON = RACINE / "jeton-dashboard"
MDP = RACINE / "mot-de-passe-dashboard"
ENV_DASH = RACINE / "dashboard.env"
VOIX_FICHIER = RACINE / "voix"
LIEN = Path.home() / "onyx-connect-link.txt"
QR = Path.home() / "onyx-connect-qr.png"
DESTINATAIRE = RACINE / "telegram-destinataire"

PORT_PASSERELLE = 8890
PORT_DASHBOARD = 9119
PORT_API = 8642
PORT_PONT = 8795

SYSTEME = platform.system()          # Linux / Darwin / Windows


# --------------------------------------------------------------------------- affichage

def etape(titre: str) -> None:
    print(f"\n\033[1m{titre}\033[0m")


def ok(texte: str) -> None:
    print(f"  \033[32m✓\033[0m {texte}")


def attention(texte: str) -> None:
    print(f"  \033[33m!\033[0m {texte}")


def mourir(texte: str) -> "None":
    print(f"\n  {texte}\n", file=sys.stderr)
    sys.exit(1)


# --------------------------------------------------------------------------- outils

def python_systeme() -> str:
    """L'interpréteur avec lequel les services tourneront — celui qui a lancé ce script."""
    return sys.executable or "python3"


def commande(nom: str) -> str | None:
    return shutil.which(nom)


def http_statut(url: str, cle: str = "x", delai: int = 5) -> int:
    """Le code HTTP d'une adresse, ou 0 si rien ne répond. Jamais d'exception."""
    requete = urllib.request.Request(url, headers={"Authorization": "Bearer " + cle})
    try:
        with urllib.request.urlopen(requete, timeout=delai) as reponse:
            return reponse.status
    except urllib.error.HTTPError as panne:
        return panne.code
    except Exception:
        return 0


def tirer_secret(octets: int = 32) -> str:
    return secrets.token_urlsafe(octets)


def ecrire_secret(chemin: Path, valeur: str) -> None:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(valeur + "\n", encoding="utf-8")
    try:
        chemin.chmod(0o600)
    except OSError:
        pass


def lire_secret(chemin: Path) -> str:
    try:
        return chemin.read_text(encoding="utf-8").strip()
    except OSError:
        return ""


# --------------------------------------------------------------------------- cloudflared

LIENS_CLOUDFLARED = {
    "Linux": "cloudflared-linux-amd64",
    "Darwin": "cloudflared-darwin-amd64.tgz",
    "Windows": "cloudflared-windows-amd64.exe",
}


def installer_cloudflared() -> str:
    """Le binaire, présent ou téléchargé dans le dossier de l'utilisateur. Aucun compte requis."""
    present = commande("cloudflared")
    if present:
        return present

    cible = Path.home() / (".local/bin" if SYSTEME != "Windows" else "onyx-bin")
    binaire = cible / ("cloudflared.exe" if SYSTEME == "Windows" else "cloudflared")
    if binaire.exists():
        return str(binaire)

    nom = LIENS_CLOUDFLARED.get(SYSTEME)
    if not nom:
        mourir(f"Plateforme non gérée : {SYSTEME}")
    assert nom is not None
    adresse = f"https://github.com/cloudflare/cloudflared/releases/latest/download/{nom}"
    cible.mkdir(parents=True, exist_ok=True)
    print(f"  téléchargement de cloudflared ({SYSTEME})…")
    try:
        urllib.request.urlretrieve(adresse, cible / nom)
    except Exception as panne:  # noqa: BLE001
        mourir(f"Téléchargement de cloudflared impossible : {panne}\n"
               f"  Installe-le à la main depuis {adresse}")

    telecharge = cible / nom
    if nom.endswith(".tgz"):
        import tarfile
        with tarfile.open(telecharge) as archive:
            archive.extractall(cible)
        telecharge.unlink(missing_ok=True)
    elif nom.endswith(".exe"):
        os.replace(telecharge, binaire)
    else:
        os.replace(telecharge, binaire)
        binaire.chmod(0o755)
    return str(binaire)


# --------------------------------------------------------------------------- Hermes

def config_hermes() -> Path:
    return Path(os.environ.get("ONYX_HERMES_CONFIG", str(Path.home() / ".hermes" / "config.yaml")))


def cle_api_server() -> str:
    """La clé de l'`api_server`, où que soit son bloc : `platforms`, `gateway`, ou ailleurs."""
    chemin = config_hermes()
    try:
        import yaml
    except ImportError:
        yaml = None
    try:
        brut = chemin.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""

    if yaml is not None:
        try:
            cfg = yaml.safe_load(brut) or {}

            def chercher(noeud):
                if isinstance(noeud, dict):
                    for nom, valeur in noeud.items():
                        if nom == "api_server" and isinstance(valeur, dict):
                            cle = valeur.get("key")
                            if isinstance(cle, str) and cle.strip():
                                return cle.strip()
                        trouve = chercher(valeur)
                        if trouve:
                            return trouve
                return None

            trouve = chercher(cfg)
            if trouve:
                return trouve
        except Exception:  # noqa: BLE001 — une config illisible ne doit pas tuer l'installation
            pass

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
    return ""


def configurer_api_server() -> str:
    """Ajoute ce qui manque à `api_server`. Ne remplace **jamais** une clé existante.

    Un Hermes de base n'a ni `enabled`, ni port, ni clé : sans ce bloc, l'app n'a personne à qui
    parler. Et sur une installation déjà réglée, ajouter un second bloc ailleurs installe une
    exposition concurrente — le téléphone déjà configuré se met alors à recevoir des 401.
    """
    chemin = config_hermes()
    if not chemin.exists():
        return "config absente"
    try:
        import yaml
    except ImportError:
        return "PyYAML absent"

    cfg = yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}

    def chercher(noeud):
        if isinstance(noeud, dict):
            for nom, valeur in noeud.items():
                if nom == "api_server" and isinstance(valeur, dict):
                    return valeur
                trouve = chercher(valeur)
                if trouve is not None:
                    return trouve
        return None

    api = chercher(cfg)
    if api is None:
        api = cfg.setdefault("platforms", {}).setdefault("api_server", {})

    ajouts = []
    for nom, valeur in (("enabled", True), ("host", "127.0.0.1"), ("port", PORT_API)):
        if nom not in api:
            api[nom] = valeur
            ajouts.append(nom)
    if not api.get("key"):
        api["key"] = tirer_secret()
        ajouts.append("key")

    if not ajouts:
        return "deja"

    sauvegarde = chemin.with_name(f"{chemin.name}.avant-onyx-{time.strftime('%Y%m%d-%H%M%S')}")
    shutil.copy2(chemin, sauvegarde)
    with chemin.open("w", encoding="utf-8") as flux:
        yaml.safe_dump(cfg, flux, allow_unicode=True, sort_keys=False)
    return "ajoute : " + ", ".join(ajouts)


# --------------------------------------------------------------------------- services

class Services:
    """Le même contrat sur trois systèmes : installe, démarre, redémarre, état.

    Chaque système a sa façon de garder un processus vivant après la fermeture du terminal :

      Linux   systemd utilisateur (`systemctl --user`), unités dans ~/.config/systemd/user ;
      macOS   launchd (`launchctl`), agents dans ~/Library/LaunchAgents ;
      Windows une tâche planifiée à l'ouverture de session (`schtasks`).

    Le même trio est piloté partout : la passerelle, le dashboard, le tunnel — plus le pont et son
    tunnel sous Linux seulement.
    """

    def __init__(self) -> None:
        self.noms: list[str] = []

    # -- Linux --

    def _unite_linux(self, nom: str) -> Path:
        base = nom[:-len(".service")] if nom.endswith(".service") else nom
        return Path.home() / ".config/systemd/user" / f"{base}.service"

    # -- macOS --

    def _plist(self, nom: str) -> Path:
        base = nom[:-len(".service")] if nom.endswith(".service") else nom
        return Path.home() / "Library/LaunchAgents" / f"com.onyx.{base}.plist"

    # -- Windows --

    def _cmd_windows(self, nom: str) -> Path:
        base = nom[:-len(".service")] if nom.endswith(".service") else nom
        return RACINE / f"{base}.cmd"

    # -- commun --

    def installer(self, nom: str, arguments: list[str], env: dict[str, str],
                  dossier: Path, journal: Path, keepalive: bool = True) -> None:
        """Déclare un composant auprès du système, et le (re)lance.

        `nom` peut arriver avec ou sans le suffixe `.service` : il est normalisé ici, parce que
        l'ajouter deux fois produit une unité nommée `…service.service`, que rien ne lance et que
        `systemctl` déclare introuvable.
        """
        base = nom[:-len(".service")] if nom.endswith(".service") else nom
        nom = f"{base}.service"
        court = base
        self.noms.append(nom)

        if SYSTEME == "Linux":
            lignes_env = "".join(f'Environment="{k}={v}"\n' for k, v in env.items())
            contenu = (
                "[Unit]\n"
                f"Description=Onyx — {court}\n"
                "After=network-online.target\n\n"
                "[Service]\n"
                "Type=simple\n"
                f"WorkingDirectory={dossier}\n"
                f"{lignes_env}"
                f"ExecStart={' '.join(arguments)}\n"
                f"Restart={'always' if keepalive else 'no'}\n"
                "RestartSec=3\n"
                "StandardOutput=journal\n"
                "StandardError=journal\n\n"
                "[Install]\n"
                "WantedBy=default.target\n"
            )
            unite = self._unite_linux(nom)
            unite.parent.mkdir(parents=True, exist_ok=True)
            change = not unite.exists() or unite.read_text(encoding="utf-8") != contenu
            if change:
                unite.write_text(contenu, encoding="utf-8")
                subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
            subprocess.run(["systemctl", "--user", "enable", nom], capture_output=True)
            # **Un service sain n'est pas relancé.** Pour le tunnel, le relancer change son nom
            # public — et le lien déjà installé sur le téléphone meurt sans raison.
            if change or not self.actif(nom):
                subprocess.run(["systemctl", "--user", "restart", nom], capture_output=True)

        elif SYSTEME == "Darwin":
            def xml(valeur: str) -> str:
                return valeur.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

            env_xml = "".join(
                f"      <key>{xml(k)}</key><string>{xml(v)}</string>\n" for k, v in env.items())
            args_xml = "".join(f"      <string>{xml(a)}</string>\n" for a in arguments)
            contenu = (
                '<?xml version="1.0" encoding="UTF-8"?>\n'
                '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" '
                '"http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
                '<plist version="1.0">\n<dict>\n'
                f"  <key>Label</key><string>com.onyx.{court}</string>\n"
                f"  <key>ProgramArguments</key>\n  <array>\n{args_xml}  </array>\n"
                f"  <key>WorkingDirectory</key><string>{xml(str(dossier))}</string>\n"
                f"  <key>EnvironmentVariables</key>\n  <dict>\n{env_xml}  </dict>\n"
                "  <key>RunAtLoad</key><true/>\n"
                f"  <key>KeepAlive</key>{'<true/>' if keepalive else '<false/>'}\n"
                f"  <key>StandardOutPath</key><string>{xml(str(journal))}</string>\n"
                f"  <key>StandardErrorPath</key><string>{xml(str(journal))}</string>\n"
                "</dict>\n</plist>\n"
            )
            agent = self._plist(nom)
            agent.parent.mkdir(parents=True, exist_ok=True)
            change = not agent.exists() or agent.read_text(encoding="utf-8") != contenu
            if change:
                agent.write_text(contenu, encoding="utf-8")
            if change or not self.actif(nom):
                subprocess.run(["launchctl", "unload", str(agent)], capture_output=True)
                subprocess.run(["launchctl", "load", "-w", str(agent)], capture_output=True)

        else:  # Windows
            def cmd_echappe(texte: str) -> str:
                return texte.replace("^", "^^").replace("&", "^&").replace("|", "^|")

            prefixe = "".join(f'set "{k}={v}"\n' for k, v in env.items())
            ligne = " ".join(f'"{a}"' if " " in a else a for a in arguments)
            script = (
                "@echo off\r\n"
                f"rem Onyx: {court}. Started by the sign-in scheduled task.\r\n"
                f"cd /d \"{dossier}\"\r\n"
                f"{prefixe}"
                f"start \"onyx-{court}\" /min {cmd_echappe(ligne)}\r\n"
            )
            fichier = self._cmd_windows(nom)
            existant = fichier.read_text(encoding="utf-8") if fichier.exists() else ""
            change = existant != script
            if change:
                # ASCII strict : cmd.exe lit les .cmd avec la page de code du poste, et un tiret
                # cadratin en UTF-8 y apparaît en charabia.
                fichier.write_text(script, encoding="ascii", errors="replace")
                subprocess.run(["schtasks", "/Delete", "/TN", f"Onyx {court}", "/F"],
                               capture_output=True)
                subprocess.run(["schtasks", "/Create", "/F", "/SC", "ONLOGON",
                                "/TN", f"Onyx {court}", "/TR", f'"{fichier}"'],
                               capture_output=True)
            deja = subprocess.run(["schtasks", "/Query", "/TN", f"Onyx {court}"],
                                  capture_output=True).returncode == 0
            if change or not deja:
                subprocess.Popen(f'"{fichier}"', shell=True,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def actif(self, nom: str) -> bool:
        if SYSTEME == "Linux":
            return subprocess.run(["systemctl", "--user", "is-active", "--quiet", nom],
                                  capture_output=True).returncode == 0
        if SYSTEME == "Darwin":
            sortie = subprocess.run(["launchctl", "list"], capture_output=True, text=True).stdout
            return f"com.onyx.{nom.split('.')[0]}" in sortie
        return True

    def redemarrer_hermes(self) -> bool:
        """La configuration de Hermes n'est lue qu'au démarrage : sans relance, une clé écrite à
        l'instant est refusée (401) alors que le fichier la montre bien."""
        if SYSTEME == "Linux" and subprocess.run(
                ["systemctl", "--user", "list-unit-files"], capture_output=True,
                text=True).stdout.count("hermes-gateway.service"):
            return subprocess.run(["systemctl", "--user", "restart", "hermes-gateway.service"],
                                  capture_output=True).returncode == 0
        if SYSTEME == "Darwin":
            sortie = subprocess.run(["launchctl", "list"], capture_output=True, text=True).stdout
            if "hermes" in sortie:
                subprocess.run(["launchctl", "stop", "com.hermes.gateway"], capture_output=True)
                subprocess.run(["launchctl", "start", "com.hermes.gateway"], capture_output=True)
                return True
        return False


# --------------------------------------------------------------------------- lien

def journal_tunnel(services: Services, nom: str) -> Path:
    """Le journal du tunnel, lu là où le service écrit vraiment (une surcharge peut le déplacer)."""
    defaut = RACINE / f"{nom}.log"
    if SYSTEME == "Linux":
        sortie = subprocess.run(["systemctl", "--user", "show", nom, "-p", "ExecStart"],
                                capture_output=True, text=True).stdout
        trouve = re.search(r"--logfile\s+(\S+?)(?:[;\s\"]|$)", sortie)
        if trouve:
            return Path(trouve.group(1))
    elif SYSTEME == "Darwin":
        agent = services._plist(nom)
        try:
            texte = agent.read_text(encoding="utf-8")
            trouve = re.search(r"--logfile</string>|--logfile</string>\s*<string>([^<]+)", texte)
            if trouve:
                return Path(trouve.group(1))
        except OSError:
            pass
    return defaut


def adresse_tunnel(nom: str, services: Services) -> str:
    chemin = journal_tunnel(services, nom)
    try:
        contenu = chemin.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    trouve = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", contenu)
    return trouve[-1] if trouve else ""


def lien(serveur: str, cle: str, voix: str, pont: str, pont_jeton: str) -> str:
    from urllib.parse import urlencode
    champs = [("v", "1"), ("server", serveur), ("key", cle), ("model", "hermes-agent")]
    if voix:
        champs.append(("voices", voix))
    if pont:
        champs.append(("bridge", pont))
    if pont_jeton:
        champs.append(("bridge-token", pont_jeton))
    champs.append(("lang", "fr"))
    return "onyx://connect?" + urlencode(champs)


def _largeur_terminal() -> int:
    return shutil.get_terminal_size((80, 24)).columns


def _python_avec_segno() -> str | None:
    """L'interpréteur qui sait faire des QR — le courant, ou celui du venv d'Onyx.

    Le script peut tourner sous n'importe quel Python (celui de Hermes, celui du système) ; on ne
    suppose donc pas que `segno` soit importable ici. S'il ne l'est pas, on lui prépare son propre
    environnement et on lui parle par sous-processus : cela marche quel que soit l'interpréteur
    appelant, au lieu d'insérer un chemin `site-packages` qui dépend de la version.
    """
    try:
        import segno  # noqa: F401
        return sys.executable
    except ImportError:
        pass

    venv = RACINE / "venv"
    python = venv / ("Scripts/python.exe" if SYSTEME == "Windows" else "bin/python")
    if not python.exists():
        subprocess.run([python_systeme(), "-m", "venv", str(venv)], capture_output=True)

    if subprocess.run([str(python), "-c", "import segno"], capture_output=True).returncode != 0:
        subprocess.run([str(python), "-m", "pip", "install", "--quiet",
                        "--disable-pip-version-check", "segno"], capture_output=True)
    if subprocess.run([str(python), "-c", "import segno"], capture_output=True).returncode != 0:
        return None
    return str(python)


_SCRIPT_QR = (
    "import sys, segno\n"
    "if sys.argv[1] == 'png':\n"
    "    segno.make(sys.argv[2], error='m').save(sys.argv[3], scale=8, border=2)\n"
    "else:\n"
    "    q = segno.make(sys.argv[2], error='l')\n"
    "    for row in q.matrix:\n"
    "        print(''.join('1' if c else '0' for c in row))\n"
)


def rendre_qr(texte: str) -> str:
    """Le QR dans le terminal, **compact** et seulement s'il rentre.

    Un lien `onyx://connect` fait plus de trois cents caractères, donc le code est large. Rendu
    naïvement — deux caractères pleins par module — il occupait une centaine de colonnes et une
    soixantaine de lignes : il débordait de l'écran et ne se scannait plus.

    Trois choix le rendent lisible :
      * **une colonne par module** au lieu de deux ;
      * des **demi-blocs** (▀) : chaque ligne de texte porte deux lignes du code, la hauteur est
        divisée par deux ;
      * la correction d'erreur la plus faible pour l'écran (un lien de 300 caractères passe de la
        version 15 à la version 12, soit 69 colonnes au lieu de 81), et la moyenne pour le fichier
        PNG, qui peut être photographié.

    Rend `"ok"`, `"trop-large"` ou `"pas-encodeur"` — trois issues distinctes, parce qu'elles
    n'appellent pas le même conseil.
    """
    python = _python_avec_segno()
    if python is None:
        return "pas-encodeur"

    subprocess.run([python, "-c", _SCRIPT_QR, "png", texte, str(QR)], capture_output=True)
    sortie = subprocess.run([python, "-c", _SCRIPT_QR, "matrice", texte],
                            capture_output=True, text=True)
    if sortie.returncode != 0 or not sortie.stdout.strip():
        return "pas-encodeur"

    matrice = [[c == "1" for c in ligne] for ligne in sortie.stdout.splitlines() if ligne]
    marge = 2
    largeur_modules = len(matrice[0]) + 2 * marge
    taille = shutil.get_terminal_size((80, 24))

    # **Un code trop dense n'est pas rendu dans le terminal.** Un lien `onyx://connect` fait
    # 65 modules : en demi-blocs cela tient en colonnes, mais dépend de l'interligne exact, de la
    # police et du nombre de couleurs du terminal — trois choses qui varient d'une machine à
    # l'autre, et un QR à moitié cassé est pire qu'aucun QR. Au-delà de 45 modules, l'image
    # prend le relais : elle est fidèle au bit près, elle s'ouvre, et elle se scanne.
    if largeur_modules > 45:
        return "image"
    if largeur_modules > taille.columns - 4:
        return "trop-large"

    vide = [False] * largeur_modules
    grille = [vide[:] for _ in range(marge)]
    for ligne in matrice:
        grille.append([False] * marge + ligne + [False] * marge)
    grille.extend(vide[:] for _ in range(marge))
    if len(grille) % 2:
        grille.append(vide[:])

    # La hauteur compte autant que la largeur : un code qui tient en colonnes mais pousse les
    # instructions hors de l'écran oblige à faire défiler pour lire ce qu'il faut faire ensuite.
    if len(grille) // 2 + 3 > taille.lines:
        return "trop-haut"

    print()
    print("  \033[2mscanne ce code avec le téléphone\033[0m")
    for index in range(0, len(grille), 2):
        haut, bas = grille[index], grille[index + 1]
        morceaux = []
        for x in range(largeur_modules):
            avant = "\033[38;5;255m" if haut[x] else "\033[38;5;232m"
            arriere = "\033[48;5;232m" if bas[x] else "\033[48;5;255m"
            morceaux.append(avant + arriere + "▀")
        print("  " + "".join(morceaux) + "\033[0m")
    print()
    return "ok"


def ouvrir_image(chemin: Path) -> bool:
    """Ouvre une image dans la visionneuse du système. Silencieux si ça échoue."""
    commandes = {
        "Linux": ["xdg-open", str(chemin)],
        "Darwin": ["open", str(chemin)],
        "Windows": ["cmd", "/c", "start", "", str(chemin)],
    }
    commande = commandes.get(SYSTEME)
    if not commande or not commande[0]:
        return False
    try:
        subprocess.Popen(commande, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return True
    except Exception:  # noqa: BLE001
        return False


def _encadre(texte: str, titre: str) -> None:
    """Le lien dans un cadre, coupé proprement : lisible même sur un terminal étroit."""
    colonnes = max(40, min(_largeur_terminal() - 4, 100))
    interieur = colonnes - 4
    lignes: list[str] = []
    reste = texte
    while reste:
        lignes.append(reste[:interieur])
        reste = reste[interieur:]
    print("  ┌─ " + titre + " " + "─" * max(0, colonnes - 5 - len(titre)) + "┐")
    for ligne in lignes:
        print("  │ " + ligne.ljust(interieur) + " │")
    # Largeur du bas = celle du contenu ("  │ " + interieur + " │" = interieur + 6), moins le
    # coin lui-même et l'indentation de deux espaces.
    print("  └" + "─" * (interieur + 2) + "┘")


# --------------------------------------------------------------------------- principal

def donner_lien(services: Services, ouvrir: bool = True) -> None:
    cle = lire_secret(CLE)
    if not cle:
        mourir("Aucune clé : lance le script sans --link pour installer d'abord.")
    serveur = adresse_tunnel("onyx-one-tunnel.service", services)
    if not serveur:
        mourir("Aucun tunnel de passerelle publié. Relance le script sans --link.")
    voix = lire_secret(VOIX_FICHIER)
    if not voix:
        voix = adresse_tunnel("kokoro-tunnel.service", services)
    # Le pont : son tunnel peut mettre quelques secondes de plus que la passerelle à publier son
    # adresse. On l'attend un peu — un lien qui porte le jeton sans l'adresse donne un écran
    # « Ordinateur » muet, sans que rien ne dise pourquoi.
    pont = ""
    for _ in range(10):
        pont = adresse_tunnel("onyx-computer-tunnel.service", services)
        if pont:
            break
        time.sleep(2)
    # Pas d'adresse, pas de jeton : les deux champs voyagent ensemble ou pas du tout.
    pont_jeton = lire_secret(Path.home() / ".onyx-computer-bridge" / "token") if pont else ""

    texte = lien(serveur, cle, voix, pont, pont_jeton)
    LIEN.write_text(texte + "\n", encoding="utf-8")
    try:
        LIEN.chmod(0o600)
    except OSError:
        pass

    etape("Le lien")
    print()
    _encadre(texte, "à coller dans l'app")
    print(f"\n  aussi dans {LIEN}")
    issue = rendre_qr(texte)
    if issue == "ok":
        print(f"  QR enregistré : {QR}")
    elif issue in ("image", "trop-large", "trop-haut"):
        # L'image est fidèle au bit près : on l'ouvre, plutôt que de dessiner un code que le
        # terminal peut déformer. **Jamais depuis le chien de garde** : une fenêtre qui s'ouvre
        # toute seule sur le bureau deux fois par heure serait une nuisance, pas un service.
        ouvert = ouvrir_image(QR) if ouvrir else False
        print(f"  QR : {QR}")
        if not ouvert and ouvrir:
            attention("ouvre cette image pour scanner :")
            attention(f"    {QR}")
    else:
        attention("Pas d'encodeur QR disponible : le lien se colle à la main.")


def rafraichir(services: Services) -> int:
    """Le lien a-t-il vieilli ? Un tunnel « quick » change de nom quand cloudflared redémarre.

    Appelé toutes les deux minutes par le planificateur du système : ne fait rien tant que le nom
    public est celui que porte déjà le lien, et le réécrit — puis le pousse à l'utilisateur si un
    destinataire est configuré — dès qu'il en change.
    """
    cle = lire_secret(CLE)
    if not cle:
        return 0
    actuel = adresse_tunnel("onyx-one-tunnel.service", services)
    if not actuel:
        return 0                      # tunnel muet : le laisser revenir de lui-même

    ancien = ""
    try:
        from urllib.parse import parse_qsl, urlparse
        ancien = dict(parse_qsl(urlparse(LIEN.read_text(encoding="utf-8").strip()).query)).get("server", "")
    except (OSError, ValueError):
        ancien = ""

    if ancien == actuel:
        return 0

    donner_lien(services, ouvrir=False)
    destinataire = lire_secret(DESTINATAIRE)
    if destinataire:
        hermes = commande("hermes")
        if hermes:
            subprocess.run([hermes, "send", "--to", destinataire,
                            "Le tunnel Onyx a changé de nom — voici le lien courant :",
                            LIEN.read_text(encoding="utf-8").strip()],
                           capture_output=True, timeout=120)
    return 0


def installer_rafraichissement(services: Services) -> None:
    """Le planificateur qui appelle `--refresh` toutes les deux minutes."""
    python = python_systeme()
    script = str(Path(__file__).resolve())
    nom = "onyx-link-watchdog.service"

    if SYSTEME == "Linux":
        unite = services._unite_linux(nom)
        unite.parent.mkdir(parents=True, exist_ok=True)
        unite.write_text(
            "[Unit]\nDescription=Onyx — régénère le lien quand le tunnel change de nom\n\n"
            f"[Service]\nType=oneshot\nExecStart={python} {script} --refresh\n",
            encoding="utf-8")
        minuterie = unite.with_suffix(".timer")
        minuterie.write_text(
            "[Unit]\nDescription=Vérifie le nom du tunnel Onyx toutes les deux minutes\n\n"
            "[Timer]\nOnBootSec=90\nOnUnitActiveSec=120\nUnit=onyx-link-watchdog.service\n\n"
            "[Install]\nWantedBy=timers.target\n", encoding="utf-8")
        subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
        subprocess.run(["systemctl", "--user", "enable", "--now", minuterie.name],
                       capture_output=True)

    elif SYSTEME == "Darwin":
        agent = Path.home() / "Library/LaunchAgents/com.onyx.watchdog.plist"
        agent.parent.mkdir(parents=True, exist_ok=True)
        agent.write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" '
            '"http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0">\n<dict>\n'
            "  <key>Label</key><string>com.onyx.watchdog</string>\n"
            "  <key>ProgramArguments</key>\n  <array>\n"
            f"    <string>{python}</string>\n    <string>{script}</string>\n"
            "    <string>--refresh</string>\n  </array>\n"
            "  <key>StartInterval</key><integer>120</integer>\n"
            "  <key>RunAtLoad</key><true/>\n"
            f"  <key>StandardOutPath</key><string>{RACINE}/watchdog.log</string>\n"
            f"  <key>StandardErrorPath</key><string>{RACINE}/watchdog.log</string>\n"
            "</dict>\n</plist>\n", encoding="utf-8")
        subprocess.run(["launchctl", "unload", str(agent)], capture_output=True)
        subprocess.run(["launchctl", "load", "-w", str(agent)], capture_output=True)

    else:
        subprocess.run(["schtasks", "/Delete", "/TN", "Onyx link watchdog", "/F"],
                       capture_output=True)
        subprocess.run(["schtasks", "/Create", "/F", "/SC", "MINUTE", "/MO", "2",
                        "/TN", "Onyx link watchdog",
                        "/TR", f'"{python}" "{script}" --refresh'], capture_output=True)


def main() -> int:
    analyseur = argparse.ArgumentParser(description="Premier démarrage d'Onyx")
    analyseur.add_argument("--link", action="store_true", help="redonner seulement le lien courant")
    analyseur.add_argument("--refresh", action="store_true",
                           help="régénérer le lien seulement si le tunnel a changé de nom")
    analyseur.add_argument("--voices", metavar="URL", help="adresse d'un serveur TTS Kokoro-compatible")
    options = analyseur.parse_args()

    RACINE.mkdir(parents=True, exist_ok=True)

    if options.voices:
        ecrire_secret(VOIX_FICHIER, options.voices)
        ok(f"adresse des voix enregistrée : {options.voices}")

    services = Services()

    if options.link:
        donner_lien(services)
        return 0

    if options.refresh:
        return rafraichir(services)

    # --- 1. ce qui doit exister -------------------------------------------------------------
    etape("1/5  Ce qui doit être là")
    hermes = commande("hermes")
    if not hermes:
        mourir("`hermes` est introuvable. Installe Hermes d'abord : https://hermes-agent.nousresearch.com/docs")
    assert hermes is not None
    ok(f"Hermes trouvé ({hermes})")

    cloudflared = installer_cloudflared()
    ok(f"cloudflared : {cloudflared}")

    if SYSTEME == "Linux":
        ok("systemd utilisateur disponible")
    elif SYSTEME == "Darwin":
        ok("launchd disponible")
    else:
        ok("planificateur de tâches Windows disponible")
        attention("le pont « Ordinateur » n'existe que sous Linux : l'écran Ordinateur restera vide")

    # Copie locale de la passerelle : le service ne doit pas dépendre d'un clone qui bougera.
    source = Path(__file__).resolve().parent / "onyx-one.py"
    if source.exists() and source.resolve() != (RACINE / "onyx-one.py").resolve():
        texte = source.read_text(encoding="utf-8")
        actuel = RACINE / "onyx-one.py"
        if not actuel.exists() or actuel.read_text(encoding="utf-8") != texte:
            actuel.write_text(texte, encoding="utf-8")
            ok("passerelle Onyx One installée")

    # --- 2. l'exposition de Hermes ----------------------------------------------------------
    etape("2/5  Hermes et son api_server")
    if http_statut(f"http://127.0.0.1:{PORT_API}/v1/models") == 0:
        changement = configurer_api_server()
        if changement == "deja":
            ok("api_server déjà configuré")
        elif changement.startswith("ajoute"):
            ok(f"api_server configuré — {changement}")
            if services.redemarrer_hermes():
                ok("passerelle Hermes relancée")
            else:
                attention("relance ta passerelle Hermes, puis relance ce script")
            for _ in range(20):
                if http_statut(f"http://127.0.0.1:{PORT_API}/v1/models") != 0:
                    break
                time.sleep(2)
        else:
            attention(f"api_server : {changement}")
    if http_statut(f"http://127.0.0.1:{PORT_API}/v1/models") == 0:
        mourir(f"Hermes ne répond pas sur 127.0.0.1:{PORT_API}. Vérifie {config_hermes()} "
               f"(platforms.api_server : enabled, host, port, key) puis relance.")
    ok(f"Hermes répond sur {PORT_API}")

    # --- 3. les secrets ---------------------------------------------------------------------
    etape("3/5  Les secrets locaux")
    for chemin, nom, taille in ((CLE, "clé de l'app", 32),
                                (JETON, "jeton du dashboard", 32),
                                (MDP, "mot de passe du dashboard", 24)):
        if lire_secret(chemin):
            ok(f"{nom} déjà présent (inchangé)")
        else:
            ecrire_secret(chemin, tirer_secret(taille))
            ok(f"{nom} créé")

    try:
        import base64
        import hashlib
        sel = secrets.token_bytes(16)
        mdp = lire_secret(MDP)
        cle_derivee = hashlib.scrypt(mdp.encode(), salt=sel, n=2 ** 14, r=8, p=1, dklen=32, maxmem=0)
        empreinte = "scrypt$16384$8$1$%s$%s" % (
            base64.b64encode(sel).decode(), base64.b64encode(cle_derivee).decode())
    except Exception:  # noqa: BLE001
        empreinte = ""
    ecrire_secret(ENV_DASH, "\n".join([
        "HERMES_DASHBOARD_BASIC_AUTH_USERNAME=onyx",
        f"HERMES_DASHBOARD_BASIC_AUTH_PASSWORD_HASH={empreinte}",
        f"HERMES_DASHBOARD_SESSION_TOKEN={lire_secret(JETON)}",
    ]))
    ok("identifiants du dashboard écrits")

    # --- 4. les services --------------------------------------------------------------------
    etape("4/5  Les services")
    python = python_systeme()
    hermes_cmd = hermes

    services.installer(
        "onyx-dashboard.service",
        [hermes_cmd, "serve", "--port", str(PORT_DASHBOARD), "--host", "127.0.0.1"],
        {k: v for k, v in (ligne.split("=", 1)
                           for ligne in lire_secret(ENV_DASH).splitlines() if "=" in ligne)},
        Path.home() / ".hermes", RACINE / "dashboard.log")
    ok("dashboard installé")

    journal_pass = RACINE / "tunnel.log"
    services.installer(
        "onyx-one.service",
        [python, str(RACINE / "onyx-one.py")],
        {
            "ONYX_ONE_HOST": "127.0.0.1",
            "ONYX_ONE_PORT": str(PORT_PASSERELLE),
            "ONYX_ONE_KEY_FILE": str(CLE),
            "ONYX_ONE_DASH_TOKEN_FILE": str(JETON),
            "ONYX_ONE_VOICES": "auto",
            "ONYX_ONE_VOICES_LOG": str(Path.home() / "kokoro-tts" / "tunnel.log"),
            "ONYX_ONE_BRIDGE_LOG": str(Path.home() / ".onyx-computer-bridge" / "tunnel.log"),
            "ONYX_ONE_BRIDGE_TOKEN_FILE": str(Path.home() / ".onyx-computer-bridge" / "token"),
            "ONYX_ONE_LANG": "fr",
            "ONYX_ONE_MODEL": "hermes-agent",
        },
        RACINE, journal_pass)
    ok("passerelle une-clé installée")

    services.installer(
        "onyx-one-tunnel.service",
        [cloudflared, "tunnel", "--url", f"http://127.0.0.1:{PORT_PASSERELLE}",
         "--no-autoupdate", "--logfile", str(journal_pass), "--loglevel", "info"],
        {}, RACINE, journal_pass)
    ok("tunnel installé")

    if SYSTEME == "Linux":
        pont_src = Path(__file__).resolve().parent / "onyx-computer-bridge"
        pont_dst = Path.home() / ".onyx-computer" / "tools" / "onyx-computer-bridge"
        pont_jeton = Path.home() / ".onyx-computer-bridge"
        if pont_dst.joinpath("bridge.py").exists() or pont_src.exists():
            if pont_src.exists():
                shutil.copytree(pont_src, pont_dst, dirs_exist_ok=True)
            venv = pont_jeton / "venv"
            python_pont = venv / "bin" / "python"
            if not python_pont.exists():
                subprocess.run([python_systeme(), "-m", "venv", str(venv)], capture_output=True)
            subprocess.run([str(python_pont), "-m", "pip", "install", "--quiet",
                            "python-xlib", "websockets"], capture_output=True)
            if not lire_secret(pont_jeton / "token"):
                ecrire_secret(pont_jeton / "token", secrets.token_hex(24))
            affichage = os.environ.get("DISPLAY", ":0")
            services.installer(
                "onyx-computer-bridge.service",
                [str(python_pont), str(pont_dst / "bridge.py"), "--display", affichage,
                 "--port", str(PORT_PONT), "--fps", "6", "--turbo-fps", "12"],
                {"DISPLAY": affichage, "PYTHONUNBUFFERED": "1"},
                pont_dst, pont_jeton / "bridge.log")
            services.installer(
                "onyx-computer-tunnel.service",
                [cloudflared, "tunnel", "--url", f"http://127.0.0.1:{PORT_PONT}",
                 "--no-autoupdate", "--logfile", str(pont_jeton / "tunnel.log"),
                 "--loglevel", "info"],
                {}, pont_jeton, pont_jeton / "tunnel.log")
            ok("pont « Ordinateur » et son tunnel installés")
        else:
            attention("code du pont absent (tools/onyx-computer-bridge)")

    # --- 5. le tunnel public, puis le lien ---------------------------------------------------
    etape("5/5  Le tunnel public")
    serveur = ""
    for _ in range(30):
        serveur = adresse_tunnel("onyx-one-tunnel.service", services)
        if serveur:
            break
        time.sleep(2)
    if not serveur:
        mourir(f"Le tunnel n'a pas publié d'adresse en 60 s. Journal : {journal_pass}")

    # Un nom publié n'est pas un tunnel qui sert : Cloudflare l'annonce avant que l'arête ne
    # réponde, et donner le lien à cet instant produit un 530 chez l'utilisateur.
    #
    # Le nom est **relu à chaque tour** : un journal peut contenir plusieurs adresses (une
    # relance, une surcharge d'unité), et juger sur la première lue condamne un tunnel qui vient
    # justement de changer de nom.
    repond = False
    for _ in range(30):
        serveur = adresse_tunnel("onyx-one-tunnel.service", services) or serveur
        if http_statut(f"{serveur}/health", delai=8) == 200:
            ok(f"tunnel : {serveur} (il répond)")
            repond = True
            break
        time.sleep(2)
    if not repond:
        attention(f"le tunnel ne répond pas encore : {serveur}")

    installer_rafraichissement(services)
    ok("chien de garde du lien installé (toutes les deux minutes)")

    donner_lien(services)

    print("""
  Sur le téléphone :
    1. installe l'APK de la dernière release (github.com/JemaOS/hermes-onyx/releases/latest) ;
    2. au premier écran, scanne le QR ci-dessus — ou colle le lien ;
    3. la conversation, les voix, les compétences et les outils arrivent ensemble.

  Plus tard :  python3 tools/onyx-setup.py --link
""")
    return 0


if __name__ == "__main__":
    sys.exit(main())