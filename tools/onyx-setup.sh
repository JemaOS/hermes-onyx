#!/usr/bin/env bash
# Onyx — first start. This file is only a shortcut: all the work is in
# `onyx-setup.py`, which handles Linux (systemd), macOS (launchd) and Windows (Task Scheduler).
#
#     ./tools/onyx-setup.sh              install / repair, then print the link and its QR
#     ./tools/onyx-setup.sh --link       print the current link again
#     ./tools/onyx-setup.sh --voices URL address of a Kokoro-compatible TTS server
set -euo pipefail
exec python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/onyx-setup.py" "$@"
