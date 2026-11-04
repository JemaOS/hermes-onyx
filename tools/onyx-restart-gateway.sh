#!/usr/bin/env bash
# Redémarre la passerelle Hermes pour qu'elle relise sa configuration.
#
# Pourquoi un fichier séparé : la configuration n'est lue qu'au démarrage de la passerelle, donc
# une clé écrite à l'instant est refusée (`401`) alors que le fichier la montre bien. Mais ce
# redémarrage interrompt les tours en cours — il reste donc une opération distincte, appelée par
# `onyx-setup.sh` **uniquement** quand la configuration a réellement changé.

set -euo pipefail

if systemctl --user list-unit-files 2>/dev/null | grep -q '^hermes-gateway\.service'; then
  systemctl --user restart hermes-gateway.service && echo "relancee (unite systemd)"
  exit 0
fi

# Pas d'unité : Hermes tourne lancé à la main. On ne peut pas le relancer sans interrompre ce qui
# tourne, et ce n'est pas à ce script de le tuer.
echo "aucune unite systemd hermes-gateway — relance ta passerelle a la main"
exit 1