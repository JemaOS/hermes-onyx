#!/usr/bin/env bash
# Onyx — premier démarrage. Ce fichier n'est qu'un raccourci : tout le travail est dans
# `onyx-setup.py`, qui gère Linux (systemd), macOS (launchd) et Windows (planificateur de tâches).
#
#     ./tools/onyx-setup.sh              installe / répare, puis donne le lien et son QR
#     ./tools/onyx-setup.sh --link       redonne le lien courant
#     ./tools/onyx-setup.sh --voices URL adresse d'un serveur TTS Kokoro-compatible
set -euo pipefail
exec python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/onyx-setup.py" "$@"
