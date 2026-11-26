#!/usr/bin/env bash
# Restart the Hermes gateway so it re-reads its configuration.
#
# Why a separate file: the configuration is only read when the gateway starts, so
# a key written just now is rejected (`401`) even though the file clearly shows it. But this
# restart interrupts in-flight turns — so it remains a separate operation, called by
# `onyx-setup.sh` **only** when the configuration has actually changed.

set -euo pipefail

if systemctl --user list-unit-files 2>/dev/null | grep -q '^hermes-gateway\.service'; then
  systemctl --user restart hermes-gateway.service && echo "relancee (unite systemd)"
  exit 0
fi

# No unit: Hermes runs started by hand. We cannot restart it without interrupting what
# is running, and it is not this script's job to kill it.
echo "aucune unite systemd hermes-gateway — relance ta passerelle a la main"
exit 1