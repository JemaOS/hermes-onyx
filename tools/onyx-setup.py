#!/usr/bin/env python3
"""Onyx — first start, one command, on Linux, macOS and Windows.

This script does what a new user cannot guess:

  1. checks what must exist (Hermes, cloudflared) and **installs it if needed**;
  2. enables exposing Hermes to the app (`api_server`), adding only what is missing;
  3. draws **one** key and the local secrets, in 0600;
  4. installs the components as system services — systemd on Linux, launchd on macOS,
     a sign-in task on Windows — so they come back on their own;
  5. waits for the tunnel to **really answer**, then prints the `onyx://connect` link and its QR.

It is re-runnable: run again, it changes neither the key, nor the tunnel, nor the already installed link.

    python3 tools/onyx-setup.py                 install / repair, then print the link
    python3 tools/onyx-setup.py --link          print only the current link
    python3 tools/onyx-setup.py --voices URL    address of a Kokoro-compatible TTS server

What does not follow everywhere: the "Computer" bridge captures an X11 screen — it exists only on
Linux. Elsewhere, the app's Computer screen will stay empty, and the script says so instead of
half-installing it.
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import os
import stat
import platform
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
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


# --------------------------------------------------------------------------- display

def etape(titre: str) -> None:
    print(f"\n\033[1m{titre}\033[0m")


def ok(texte: str) -> None:
    print(f"  \033[32m✓\033[0m {texte}")


def attention(texte: str) -> None:
    print(f"  \033[33m!\033[0m {texte}")


def mourir(texte: str) -> "None":
    print(f"\n  {texte}\n", file=sys.stderr)
    sys.exit(1)


# --------------------------------------------------------------------------- tools

def python_systeme() -> str:
    """The interpreter the services will run under — the one that launched this script."""
    return sys.executable or "python3"


def commande(nom: str) -> str | None:
    return shutil.which(nom)


def http_statut(url: str, cle: str | None = None, delai: int = 5) -> int:
    """The HTTP code of an address, or 0 if nothing answers. Never an exception.

    [cle]: the `api_server` key. The probe used to send a hardcoded `"x"` — and the gateway
    logged **a warning per probe** ("API server rejected invalid API key", /v1/models),
    whereas the 401 already said it all: the server is there, the key is wrong. So we send the key
    the configuration file carries ([cle_api_server]); if there is none yet — first
    installation — we send **no** header at all, rather than a false key.
    """
    if cle is None:
        cle = cle_api_server()
    entetes = {"Authorization": "Bearer " + cle} if cle else {}
    requete = urllib.request.Request(url, headers=entetes)
    try:
        with urllib.request.urlopen(requete, timeout=delai) as reponse:
            return reponse.status
    except urllib.error.HTTPError as panne:
        return panne.code
    except Exception:
        return 0


def tirer_secret(octets: int = 32) -> str:
    return secrets.token_urlsafe(octets)


def _acl_privee(chemin: Path) -> None:
    # chmod on Windows does not restrict read access; use the current user's SID.
    import csv
    sortie = subprocess.run(["whoami", "/user", "/fo", "csv", "/nh"],
                            capture_output=True, text=True, check=True).stdout
    sid = next(csv.reader(sortie.strip().splitlines()))[1]
    if not re.fullmatch(r"S-1-[0-9-]+", sid):
        raise OSError("Cannot determine the current user's SID")
    # Apply a complete protected DACL in one operation (no permissive /reset window).
    type_acl = "DirectorySecurity" if chemin.is_dir() else "FileSecurity"
    heritage = "OICI" if chemin.is_dir() else ""
    cible = str(chemin).replace("'", "''")
    script = (
        "$ErrorActionPreference = 'Stop'; "
        f"$acl = New-Object System.Security.AccessControl.{type_acl}; "
        f"$acl.SetSecurityDescriptorSddlForm('D:P(A;{heritage};FA;;;{sid})'); "
        f"Set-Acl -LiteralPath '{cible}' -AclObject $acl"
    )
    subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
                   capture_output=True, check=True)


@contextmanager
def fichier_prive(chemin: Path):
    """Stage privately beside the target, then replace its directory entry, not its symlink."""
    chemin.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.TemporaryDirectory(prefix=".onyx-", dir=chemin.parent) as dossier:
        if os.name == "nt":
            _acl_privee(Path(dossier))
        temporaire = Path(dossier) / chemin.name
        fd = os.open(temporaire, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as flux:
            yield flux
            flux.flush()
            os.fsync(flux.fileno())
        os.replace(temporaire, chemin)


def ecrire_prive(chemin: Path, valeur: str, encoding: str = "utf-8") -> None:
    with fichier_prive(chemin) as flux:
        flux.write(valeur.encode(encoding, errors="replace"))


def proteger_fichier(chemin: Path, creer: bool = False) -> None:
    """Tighten existing regular files without following links or truncating active logs."""
    if creer:
        chemin.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if chemin.is_symlink():
        raise OSError(f"Refusing sensitive symlink: {chemin}")
    if os.name == "nt":
        if not chemin.exists() and creer:
            ecrire_prive(chemin, "")
        if chemin.exists():
            _acl_privee(chemin)
        return
    flags = os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW
    if creer:
        flags = os.O_WRONLY | os.O_APPEND | os.O_CREAT | os.O_NONBLOCK | os.O_NOFOLLOW
    try:
        fd = os.open(chemin, flags, 0o600)
    except FileNotFoundError:
        return
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            raise OSError(f"Refusing non-regular or hard-linked sensitive file: {chemin}")
        os.fchmod(fd, 0o600)
    finally:
        os.close(fd)


def proteger_artifacts() -> None:
    chemins = (CLE, JETON, MDP, ENV_DASH, VOIX_FICHIER, DESTINATAIRE, LIEN, QR,
               RACINE / "watchdog.log", RACINE / "tunnel.log", RACINE / "dashboard.log",
               RACINE / "onyx-one.log", Path.home() / ".onyx-computer-bridge" / "token",
               Path.home() / ".onyx-computer-bridge" / "bridge.log",
               Path.home() / ".onyx-computer-bridge" / "tunnel.log")
    for chemin in chemins:
        proteger_fichier(chemin)


def ecrire_secret(chemin: Path, valeur: str) -> None:
    ecrire_prive(chemin, valeur + "\n")


def lire_secret(chemin: Path) -> str:
    proteger_fichier(chemin)
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
    """The binary, present or downloaded into the user's folder. No account required."""
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
            # Publish only the expected regular binary, never archive paths or links.
            members = archive.getmembers()
            if len(members) != 1 or members[0].name not in ("cloudflared", "./cloudflared") or not members[0].isreg():
                raise ValueError("Unexpected cloudflared archive contents")
            source = archive.extractfile(members[0])
            if source is None:
                raise ValueError("Missing cloudflared binary")
            with source, fichier_prive(binaire) as output:
                shutil.copyfileobj(source, output)
        binaire.chmod(0o755)
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
    """The `api_server` key, wherever its block lives: `platforms`, `gateway`, or elsewhere."""
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
        except Exception:  # noqa: BLE001 — an unreadable config must not kill the installation
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
    """Adds what is missing to `api_server`. Never **replaces** an existing key.

    A bare Hermes has no `enabled`, no port, no key: without this block, the app has no one to
    talk to. And on an already configured installation, adding a second block elsewhere installs a competing
    exposure — the already-configured phone then starts receiving 401s.
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

    proteger_fichier(chemin)
    if not ajouts:
        return "deja"

    sauvegarde = chemin.with_name(f"{chemin.name}.avant-onyx-{time.strftime('%Y%m%d-%H%M%S')}")
    ecrire_prive(sauvegarde, chemin.read_text(encoding="utf-8"))
    ecrire_prive(chemin, yaml.safe_dump(cfg, allow_unicode=True, sort_keys=False))
    return "ajoute : " + ", ".join(ajouts)


# --------------------------------------------------------------------------- services

class Services:
    """The same contract on three systems: install, start, restart, status.

    Each system has its own way of keeping a process alive after the terminal closes:

      Linux   user systemd (`systemctl --user`), units in ~/.config/systemd/user ;
      macOS   launchd (`launchctl`), agents in ~/Library/LaunchAgents ;
      Windows a sign-in scheduled task (`schtasks`).

    The same trio is driven everywhere: the gateway, the dashboard, the tunnel — plus the bridge and its
    tunnel on Linux only.
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

    # -- common --

    def installer(self, nom: str, arguments: list[str], env: dict[str, str],
                  dossier: Path, journal: Path, keepalive: bool = True) -> None:
        """Declares a component to the system, and (re)starts it.

        `nom` may arrive with or without the `.service` suffix: it is normalized here, because
        adding it twice produces a unit named `…service.service`, which nothing starts and which
        `systemctl` declares not found.
        """
        base = nom[:-len(".service")] if nom.endswith(".service") else nom
        nom = f"{base}.service"
        court = base
        self.noms.append(nom)
        proteger_fichier(journal, creer=True)
        journal_sortie = str(journal)
        for index, argument in enumerate(arguments[:-1]):
            if argument == "--logfile":
                proteger_fichier(Path(arguments[index + 1]), creer=True)
                # cloudflared already writes this log; do not duplicate its stderr there.
                journal_sortie = "/dev/null"

        if SYSTEME == "Linux":
            unite = self._unite_linux(nom)
            environnement = unite.with_suffix(".env")
            # Environment= is exposed through systemd's D-Bus properties.
            def env_echappe(valeur: str) -> str:
                return valeur.replace("\\", "\\\\").replace('"', '\\"')
            contenu_env = "".join(f'{k}="{env_echappe(v)}"\n' for k, v in env.items())
            proteger_fichier(environnement)
            change_env = (not environnement.exists() or
                          environnement.read_text(encoding="utf-8") != contenu_env)
            if change_env:
                ecrire_prive(environnement, contenu_env)
            lignes_env = f'EnvironmentFile="{environnement}"\n'
            contenu = (
                "[Unit]\n"
                f"Description=Onyx — {court}\n"
                "After=network-online.target\n\n"
                "[Service]\n"
                "Type=simple\n"
                "UMask=0077\n"
                f"WorkingDirectory={dossier}\n"
                f"{lignes_env}"
                f"ExecStart={' '.join(arguments)}\n"
                f"Restart={'always' if keepalive else 'no'}\n"
                "RestartSec=3\n"
                f"StandardOutput={'null' if journal_sortie == '/dev/null' else 'append:' + journal_sortie}\n"
                f"StandardError={'null' if journal_sortie == '/dev/null' else 'append:' + journal_sortie}\n\n"
                "[Install]\n"
                "WantedBy=default.target\n"
            )
            unite = self._unite_linux(nom)
            unite.parent.mkdir(parents=True, exist_ok=True)
            proteger_fichier(unite)
            change = not unite.exists() or unite.read_text(encoding="utf-8") != contenu
            if change:
                ecrire_prive(unite, contenu)
                subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
            subprocess.run(["systemctl", "--user", "enable", nom], capture_output=True)
            # **A healthy service is not restarted.** For the tunnel, restarting it changes its
            # public name — and the link already installed on the phone dies for no reason.
            if change or change_env or not self.actif(nom):
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
                "  <key>Umask</key><integer>63</integer>\n"
                "  <key>RunAtLoad</key><true/>\n"
                f"  <key>KeepAlive</key>{'<true/>' if keepalive else '<false/>'}\n"
                f"  <key>StandardOutPath</key><string>{xml(journal_sortie)}</string>\n"
                f"  <key>StandardErrorPath</key><string>{xml(journal_sortie)}</string>\n"
                "</dict>\n</plist>\n"
            )
            agent = self._plist(nom)
            agent.parent.mkdir(parents=True, exist_ok=True)
            proteger_fichier(agent)
            change = not agent.exists() or agent.read_text(encoding="utf-8") != contenu
            if change:
                ecrire_prive(agent, contenu)
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
            proteger_fichier(fichier)
            existant = fichier.read_text(encoding="utf-8") if fichier.exists() else ""
            change = existant != script
            if change:
                # Strict ASCII: cmd.exe reads .cmd files with the machine's code page, and an em
                # dash in UTF-8 appears there as gibberish.
                ecrire_prive(fichier, script, encoding="ascii")
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
        """Hermes's configuration is only read at startup: without a restart, a key written
        just now is rejected (401) even though the file clearly shows it."""
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


# --------------------------------------------------------------------------- link

def journal_tunnel(services: Services, nom: str) -> Path:
    """The tunnel log, read where the service actually writes (an override may move it)."""
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
    """The interpreter that can make QRs — the current one, or Onyx's venv's.

    The script may run under any Python (Hermes's, the system's); we therefore do not
    assume `segno` is importable here. If it is not, we prepare its own
    environment and talk to it through a subprocess: this works whatever the calling
    interpreter, instead of inserting a `site-packages` path that depends on the version.
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
    "    segno.make(sys.stdin.read(), error='m').save(sys.stdout.buffer, kind='png', scale=8, border=2)\n"
    "else:\n"
    "    q = segno.make(sys.stdin.read(), error='l')\n"
    "    for row in q.matrix:\n"
    "        print(''.join('1' if c else '0' for c in row))\n"
)


def rendre_qr(texte: str, afficher: bool = True) -> str:
    """The QR in the terminal, **compact** and only if it fits.

    An `onyx://connect` link is more than three hundred characters, so the code is wide. Rendered
    naively — two full characters per module — it took up about a hundred columns and
    sixty lines: it overflowed the screen and could no longer be scanned.

    Three choices make it readable:
      * **one column per module** instead of two;
      * **half blocks** (▀): each text line carries two lines of the code, so the height is
        halved;
      * the weakest error correction for the screen (a 300-character link goes from
        version 15 to version 12, i.e. 69 columns instead of 81), and the medium one for the
        PNG file, which may be photographed.

    Returns `"ok"`, `"trop-large"` or `"pas-encodeur"` — three distinct outcomes, because they
    do not call for the same advice.
    """
    python = _python_avec_segno()
    if python is None:
        return "pas-encodeur"

    try:
        with fichier_prive(QR) as flux:
            subprocess.run([python, "-c", _SCRIPT_QR, "png"], input=texte.encode("utf-8"),
                           stdout=flux, stderr=subprocess.PIPE, check=True)
    except subprocess.CalledProcessError:
        return "pas-encodeur"
    if not afficher:
        return "image"
    sortie = subprocess.run([python, "-c", _SCRIPT_QR, "matrice"], input=texte,
                            capture_output=True, text=True)
    if sortie.returncode != 0 or not sortie.stdout.strip():
        return "pas-encodeur"

    matrice = [[c == "1" for c in ligne] for ligne in sortie.stdout.splitlines() if ligne]
    marge = 2
    largeur_modules = len(matrice[0]) + 2 * marge
    taille = shutil.get_terminal_size((80, 24))

    # **A code too dense is not rendered in the terminal.** An `onyx://connect` link is
    # 65 modules: in half blocks that fits within the columns, but depends on the exact line spacing, the
    # font and the number of terminal colors — three things that vary from one machine to
    # another, and a half-broken QR is worse than no QR. Beyond 45 modules, the image
    # takes over: it is faithful to the bit, it opens, and it scans.
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

    # Height counts as much as width: a code that fits in columns but pushes the
    # instructions off screen forces scrolling to read what to do next.
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
    """Opens an image in the system viewer. Silent if it fails."""
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
    """The link in a frame, wrapped cleanly: readable even on a narrow terminal."""
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
    # Bottom width = the content's ("  │ " + interior + " │" = interior + 6), minus the
    # corner itself and the two-space indentation.
    print("  └" + "─" * (interieur + 2) + "┘")


# --------------------------------------------------------------------------- main

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
    # The bridge: its tunnel may take a few seconds longer than the gateway to publish its
    # address. We wait a little — a link carrying the token without the address gives a silent
    # "Computer" screen, with nothing saying why.
    pont = ""
    for _ in range(10):
        pont = adresse_tunnel("onyx-computer-tunnel.service", services)
        if pont:
            break
        time.sleep(2)
    # No address, no token: the two fields travel together or not at all.
    pont_jeton = lire_secret(Path.home() / ".onyx-computer-bridge" / "token") if pont else ""

    texte = lien(serveur, cle, voix, pont, pont_jeton)
    ecrire_secret(LIEN, texte)
    if not ouvrir:
        rendre_qr(texte, afficher=False)
        return

    etape("Le lien")
    print()
    _encadre(texte, "à coller dans l'app")
    print(f"\n  aussi dans {LIEN}")
    issue = rendre_qr(texte)
    if issue == "ok":
        print(f"  QR enregistré : {QR}")
    elif issue in ("image", "trop-large", "trop-haut"):
        # The image is faithful to the bit: we open it, rather than draw a code the
        # terminal may deform. **Never from the watchdog**: a window opening on its own
        # on the desktop twice an hour would be a nuisance, not a service.
        ouvert = ouvrir_image(QR) if ouvrir else False
        print(f"  QR : {QR}")
        if not ouvert and ouvrir:
            attention("ouvre cette image pour scanner :")
            attention(f"    {QR}")
    else:
        attention("Pas d'encodeur QR disponible : le lien se colle à la main.")


def rafraichir(services: Services) -> int:
    """Has the link aged? A "quick" tunnel changes name when cloudflared restarts.

    Called every two minutes by the system scheduler: does nothing as long as the public
    name is the one the link already carries, and rewrites it — then pushes it to the user if a
    recipient is configured — as soon as it changes.
    """
    proteger_artifacts()
    cle = lire_secret(CLE)
    if not cle:
        return 0
    actuel = adresse_tunnel("onyx-one-tunnel.service", services)
    if not actuel:
        return 0                      # silent tunnel: let it come back on its own

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
            message = ("Le tunnel Onyx a changé de nom — voici le lien courant :\n"
                       + LIEN.read_text(encoding="utf-8").strip())
            try:
                resultat = subprocess.run([hermes, "send", "--to", destinataire, "--file", "-"],
                                          input=message, text=True, capture_output=True, timeout=120)
                if resultat.returncode != 0:
                    attention("Notification du lien échouée ; le fichier local reste disponible.")
            except subprocess.TimeoutExpired:
                # Child output may contain the pairing secret; never include it in diagnostics.
                attention("Notification du lien expirée ; le fichier local reste disponible.")
    return 0


def installer_rafraichissement(services: Services) -> None:
    """The scheduler that calls `--refresh` every two minutes."""
    python = python_systeme()
    script = str(Path(__file__).resolve())
    nom = "onyx-link-watchdog.service"
    proteger_fichier(RACINE / "watchdog.log", creer=True)

    if SYSTEME == "Linux":
        unite = services._unite_linux(nom)
        unite.parent.mkdir(parents=True, exist_ok=True)
        ecrire_prive(unite,
            "[Unit]\nDescription=Onyx — régénère le lien quand le tunnel change de nom\n\n"
            f"[Service]\nType=oneshot\nUMask=0077\nExecStart={python} {script} --refresh\n"
            "StandardOutput=null\nStandardError=null\n")
        minuterie = unite.with_suffix(".timer")
        ecrire_prive(minuterie,
            "[Unit]\nDescription=Vérifie le nom du tunnel Onyx toutes les deux minutes\n\n"
            "[Timer]\nOnBootSec=90\nOnUnitActiveSec=120\nUnit=onyx-link-watchdog.service\n\n"
            "[Install]\nWantedBy=timers.target\n", encoding="utf-8")
        subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
        subprocess.run(["systemctl", "--user", "enable", "--now", minuterie.name],
                       capture_output=True)

    elif SYSTEME == "Darwin":
        agent = Path.home() / "Library/LaunchAgents/com.onyx.watchdog.plist"
        agent.parent.mkdir(parents=True, exist_ok=True)
        ecrire_prive(agent,
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" '
            '"http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0">\n<dict>\n'
            "  <key>Label</key><string>com.onyx.watchdog</string>\n"
            "  <key>ProgramArguments</key>\n  <array>\n"
            f"    <string>{python}</string>\n    <string>{script}</string>\n"
            "    <string>--refresh</string>\n  </array>\n"
            "  <key>StartInterval</key><integer>120</integer>\n"
            "  <key>RunAtLoad</key><true/>\n"
            "  <key>StandardOutPath</key><string>/dev/null</string>\n"
            "  <key>StandardErrorPath</key><string>/dev/null</string>\n"
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

    RACINE.mkdir(parents=True, exist_ok=True, mode=0o700)
    proteger_artifacts()

    if options.voices:
        ecrire_secret(VOIX_FICHIER, options.voices)
        ok("adresse des voix enregistrée")

    services = Services()

    if options.link:
        donner_lien(services)
        return 0

    if options.refresh:
        return rafraichir(services)

    # --- 1. what must exist -----------------------------------------------------------------
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

    # Local copy of the gateway: the service must not depend on a clone that may move.
    source = Path(__file__).resolve().parent / "onyx-one.py"
    if source.exists() and source.resolve() != (RACINE / "onyx-one.py").resolve():
        texte = source.read_text(encoding="utf-8")
        actuel = RACINE / "onyx-one.py"
        if not actuel.exists() or actuel.read_text(encoding="utf-8") != texte:
            actuel.write_text(texte, encoding="utf-8")
            ok("passerelle Onyx One installée")

    # --- 2. exposing Hermes -----------------------------------------------------------------
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

    # --- 3. the secrets ---------------------------------------------------------------------
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

    # --- 4. the services --------------------------------------------------------------------
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
        RACINE, RACINE / "onyx-one.log")
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

    # --- 5. the public tunnel, then the link -------------------------------------------------
    etape("5/5  Le tunnel public")
    serveur = ""
    for _ in range(30):
        serveur = adresse_tunnel("onyx-one-tunnel.service", services)
        if serveur:
            break
        time.sleep(2)
    if not serveur:
        mourir(f"Le tunnel n'a pas publié d'adresse en 60 s. Journal : {journal_pass}")

    # A published name is not a tunnel that serves: Cloudflare announces it before the edge
    # answers, and giving the link at that instant produces a 530 for the user.
    #
    # The name is **re-read on every turn**: a log may contain several addresses (a
    # restart, a unit override), and judging on the first one read condemns a tunnel that has
    # just changed name.
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
