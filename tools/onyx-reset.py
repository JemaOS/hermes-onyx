#!/usr/bin/env python3
"""Onyx — remise à zéro, comme sur une machine neuve.

Efface **tout ce que l'installeur crée** : les services (systemd / launchd / tâche planifiée), les
secrets, la passerelle Onyx One, le pont, le lien et son QR. Ne touche **jamais** à Hermes : sa
configuration, ses sessions et sa base restent intactes.

À quoi ça sert : éprouver le parcours d'un nouvel utilisateur. On efface, on relance
`tools/onyx-setup.py`, et on regarde si tout revient — au lieu de le supposer.

    python3 tools/onyx-reset.py            demande confirmation
    python3 tools/onyx-reset.py --force    sans question (pour un essai automatisé)
"""

from __future__ import annotations

import argparse
import platform
import shutil
import subprocess
import sys
from pathlib import Path

RACINE = Path.home() / ".onyx-one"
PONT_JETON = Path.home() / ".onyx-computer-bridge"
PONT_CODE = Path.home() / ".onyx-computer" / "tools" / "onyx-computer-bridge"
LIEN = Path.home() / "onyx-connect-link.txt"
QR = Path.home() / "onyx-connect-qr.png"

SYSTEME = platform.system()

SERVICES_LINUX = [
    "onyx-one", "onyx-dashboard", "onyx-one-tunnel",
    "onyx-computer-bridge", "onyx-computer-tunnel",
    "onyx-link-watchdog",
]
SERVICES_MAC = ["com.onyx.onyx-one", "com.onyx.onyx-dashboard", "com.onyx.onyx-one-tunnel",
                "com.onyx.watchdog"]
SERVICES_WIN = ["Onyx onyx-one", "Onyx onyx-dashboard", "Onyx onyx-one-tunnel",
                "Onyx link watchdog"]


def dire(texte: str) -> None:
    print(f"  {texte}")


def arreter_linux() -> None:
    for nom in SERVICES_LINUX:
        subprocess.run(["systemctl", "--user", "disable", "--now", f"{nom}.service"],
                       capture_output=True)
        subprocess.run(["systemctl", "--user", "disable", "--now", f"{nom}.timer"],
                       capture_output=True)
    subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
    dossier = Path.home() / ".config/systemd/user"
    for nom in SERVICES_LINUX:
        for suffixe in (".service", ".timer"):
            (dossier / f"{nom}{suffixe}").unlink(missing_ok=True)
        # Les surcharges (`*.service.d/`) gardent d'anciennes lignes de commande : les laisser
        # ferait renaître le service avec l'ancienne configuration au premier redémarrage.
    for reste in dossier.glob("onyx-*.service.d"):
        shutil.rmtree(reste, ignore_errors=True)
    subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
    dire("services systemd retirés")


def arreter_mac() -> None:
    dossier = Path.home() / "Library/LaunchAgents"
    for nom in SERVICES_MAC:
        agent = dossier / f"{nom}.plist"
        subprocess.run(["launchctl", "unload", "-w", str(agent)], capture_output=True)
        agent.unlink(missing_ok=True)
    dire("agents launchd retirés")


def arreter_windows() -> None:
    for nom in SERVICES_WIN:
        subprocess.run(["schtasks", "/Delete", "/TN", nom, "/F"], capture_output=True)
    dire("tâches planifiées retirées")


def main() -> int:
    analyseur = argparse.ArgumentParser(description="Remise à zéro d'Onyx")
    analyseur.add_argument("--force", action="store_true", help="sans demander confirmation")
    options = analyseur.parse_args()

    print("\nCeci efface les services et secrets d'Onyx. Hermes n'est pas touché.\n")
    if not options.force:
        reponse = input("  Continuer ? [oui/non] ").strip().lower()
        if reponse not in ("oui", "o", "yes", "y"):
            print("  annulé")
            return 1

    if SYSTEME == "Linux":
        arreter_linux()
    elif SYSTEME == "Darwin":
        arreter_mac()
    else:
        arreter_windows()

    for chemin, quoi in ((RACINE, "passerelle, secrets et identifiants"),
                         (PONT_JETON, "jeton, venv et journaux du pont"),
                         (PONT_CODE, "code du pont"),
                         (LIEN, "lien"),
                         (QR, "QR")):
        if chemin.is_dir():
            shutil.rmtree(chemin, ignore_errors=True)
        elif chemin.exists():
            chemin.unlink(missing_ok=True)
        dire(f"{quoi} : {chemin}")

    print("\n  Machine comme neuve. Pour réinstaller :")
    print("     python3 tools/onyx-setup.py\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())